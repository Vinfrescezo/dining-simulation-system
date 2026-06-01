import { CANVAS_SIZE, DEFAULT_CONFIG, STUDENT_STATES } from '../utils/constants';
import { createCanteenLayout, getQueueSpot, getWaitingSpot, getWaitingRoamingSpot } from '../utils/layout';

const { PATHFINDING, QUEUEING, ORDERING, WAITING_FOR_SEAT, SEEK_SEAT, EATING, LEAVING } = STUDENT_STATES;

const HOT_DISHES = [
  { dishName: '麻辣香锅', popularityRank: 1, popularityScore: 5.0 },
  { dishName: '炙烤五花肉', popularityRank: 2, popularityScore: 4.7 },
  { dishName: '土豆泥拌饭', popularityRank: 3, popularityScore: 4.3 },
  { dishName: '北京烤鸭', popularityRank: 4, popularityScore: 4.1 },
  { dishName: '云南米线', popularityRank: 5, popularityScore: 3.8 },
  { dishName: '兰州拉面', popularityRank: 6, popularityScore: 3.6 }
];


function truncatedNormalCdf(progress, mu, sigma) {
  const safeSigma = Math.max(0.0001, sigma);
  const start = normalCdf((0 - mu) / safeSigma);
  const end = normalCdf((1 - mu) / safeSigma);
  const current = normalCdf((progress - mu) / safeSigma);
  const value = (current - start) / Math.max(0.0001, end - start);
  return Math.max(0, Math.min(1, value));
}

function normalCdf(x) {
  return 0.5 * (1 + erf(x / Math.sqrt(2)));
}

function erf(x) {
  const sign = x < 0 ? -1 : 1;
  x = Math.abs(x);
  const t = 1 / (1 + 0.3275911 * x);
  const y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
    - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
  return sign * y;
}

function randomNormal(mean, sd, min = 1) {
  const u = Math.random() || 0.0001;
  const v = Math.random() || 0.0001;
  const value = mean + sd * Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
  return Math.max(min, Math.round(value));
}

function distance(a, b) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function moveTowards(entity, target, speed) {
  const d = distance(entity, target);
  if (d <= speed || d === 0) {
    entity.x = target.x;
    entity.y = target.y;
    return true;
  }
  entity.x += ((target.x - entity.x) / d) * speed;
  entity.y += ((target.y - entity.y) / d) * speed;
  return false;
}

export class LocalDiningSimulator {
  constructor(rawConfig, handlers = {}) {
    this.config = {
      ...DEFAULT_CONFIG,
      ...rawConfig
    };
    this.handlers = handlers;
    this.layout = createCanteenLayout(this.config);
    this.tick = 0;
    this.generated = 0;
    this.lost = 0;
    this.finished = 0;
    this.studentSeq = 1;
    this.spawnAccumulator = 0;
    this.students = new Map();
    this.windows = this.layout.windows.map((win, index) => ({
      ...win,
      ...(HOT_DISHES[index % HOT_DISHES.length]),
      queue: [],
      orderingStudentId: null,
      served: 0,
      qLenSamples: [],
      waitSamples: []
    }));
    this.seats = this.layout.seats.map(seat => ({ ...seat, occupiedBy: null, occupancyTicks: 0 }));
    this.waitingSeatQueue = [];
    this.trend = [];
    this.totalQueueTime = 0;
    this.totalSeatWaitTime = 0;
    this.totalEatingTime = 0;
    this.maxCongestion = 0;
    this.timer = null;
    this.speedFactor = 1;
    this.paused = false;
  }

  start() {
    this.emitSnapshot();
    this.schedule();
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  setPaused(paused) {
    this.paused = paused;
  }

  setSpeedFactor(factor) {
    this.speedFactor = Math.max(0.5, Math.min(8, Number(factor) || 1));
    this.stop();
    this.schedule();
  }

  schedule() {
    const interval = Math.max(16, this.config.tickInterval / this.speedFactor);
    this.timer = setInterval(() => {
      if (!this.paused) this.step();
    }, interval);
  }

  step() {
    this.tick += 1;
    this.spawnStudents();
    this.updateMovingStudents();
    this.processWindows();
    this.allocateSeatsToWaitingStudents();
    this.updateEatingStudents();
    this.sampleStats();
    this.emitSnapshot();

    if (this.tick >= this.config.simDurationTick || (this.generated >= this.config.studentCount && this.students.size === 0)) {
      this.stop();
      this.handlers.onFinish?.(this.buildReport());
    }
  }

  spawnStudents() {
    const remaining = this.config.studentCount - this.generated;
    if (remaining <= 0) return;

    // 本地演示模式也采用与后端一致的“截断正态累计到达”模型。
    // 当前应累计到达人数 = 总人数 × CDF(t)，本 tick 到达人数 = 应累计人数 - 已生成人数。
    // 这样右侧到达人数曲线不会在末尾为了补齐人数而出现异常尖峰。
    const duration = Math.max(1, this.config.simDurationTick);
    const progress = Math.max(0, Math.min(1, this.tick / duration));
    const fraction = truncatedNormalCdf(progress, 0.32, 0.12);
    const expectedGenerated = this.tick >= duration
      ? this.config.studentCount
      : Math.floor(this.config.studentCount * fraction);
    const count = Math.min(remaining, Math.max(0, expectedGenerated - this.generated));

    for (let i = 0; i < count; i += 1) {
      this.createStudent();
    }
  }

  createStudent() {
    const targetWindow = this.pickShortestWindow();
    if (!targetWindow || targetWindow.queue.length >= this.config.maxQueueCapacity) {
      this.generated += 1;
      this.lost += 1;
      return;
    }

    const queueIndex = targetWindow.queue.length;
    const target = getQueueSpot(targetWindow, queueIndex);
    const id = this.studentSeq++;
    const student = {
      id,
      x: this.layout.entrance.x,
      y: this.layout.entrance.y,
      s: PATHFINDING,
      windowId: targetWindow.id,
      seatId: null,
      target,
      speed: randomNormal(4.6, 0.7, 2.8),
      queueStartTick: this.tick,
      seatWaitStartTick: null,
      orderingLeft: randomNormal(18, 5, 4),
      eatingLeft: randomNormal(480, 90, 120),
      totalWait: 0,
      totalSeatWait: 0
    };

    this.generated += 1;
    this.students.set(id, student);
    targetWindow.queue.push(id);
  }

  pickShortestWindow() {
    return this.windows.reduce((best, current) => {
      if (!best) return current;
      return current.queue.length < best.queue.length ? current : best;
    }, null);
  }

  updateMovingStudents() {
    for (const student of this.students.values()) {
      if (![PATHFINDING, SEEK_SEAT, LEAVING].includes(student.s)) continue;
      const arrived = moveTowards(student, student.target, student.speed);
      if (!arrived) continue;

      if (student.s === PATHFINDING) {
        student.s = QUEUEING;
      } else if (student.s === SEEK_SEAT) {
        student.s = EATING;
      } else if (student.s === LEAVING) {
        if (student.leaveStage === 'toLane') {
          student.leaveStage = 'toExit';
          student.target = { x: this.layout.exit.x, y: this.layout.exit.y };
        } else {
          this.students.delete(student.id);
          this.finished += 1;
        }
      }
    }

    this.refreshQueueTargets();
  }

  refreshQueueTargets() {
    for (const win of this.windows) {
      let visualIndex = 0;
      for (const studentId of win.queue) {
        const student = this.students.get(studentId);
        if (!student || student.s === ORDERING) continue;
        const spot = getQueueSpot(win, visualIndex);
        student.target = spot;
        if (student.s === QUEUEING) moveTowards(student, spot, 1.8);
        visualIndex += 1;
      }
    }
  }

  processWindows() {
    for (const win of this.windows) {
      // 清理幽灵队列：移除已不存在的学生 ID
      win.queue = win.queue.filter(id => this.students.has(id));

      // 检查当前打饭学生状态
      if (win.orderingStudentId) {
        const current = this.students.get(win.orderingStudentId);
        if (!current || current.s !== ORDERING) {
          // 学生不存在或状态异常 → 解除卡死，清掉该 ID
          win.queue = win.queue.filter(id => id !== win.orderingStudentId);
          win.orderingStudentId = null;
        } else {
          current.orderingLeft -= 1;
          if (current.orderingLeft <= 0) {
            this.finishOrdering(win, current);
          }
        }
      }

      // 没人打饭 → 从队列中找第一个已到达(QUEUEING)的学生开始服务
      if (!win.orderingStudentId && win.queue.length > 0) {
        let nextId = null;
        let nextStudent = null;
        for (const id of win.queue) {
          const s = this.students.get(id);
          if (s && s.s === QUEUEING) {
            nextId = id;
            nextStudent = s;
            break;
          }
        }
        if (nextStudent) {
          win.orderingStudentId = nextId;
          nextStudent.s = ORDERING;
          nextStudent.target = { x: win.x, y: win.y + 24 };
          nextStudent.x = win.x;
          nextStudent.y = win.y + 24;
          nextStudent.totalWait = this.tick - nextStudent.queueStartTick;
          this.totalQueueTime += nextStudent.totalWait;
          win.waitSamples.push(nextStudent.totalWait);
        }
      }
    }
  }

  finishOrdering(win, student) {
    win.queue = win.queue.filter(id => id !== student.id);
    win.orderingStudentId = null;
    win.served += 1;

    const seat = this.findBestSeat(student);
    if (seat) {
      this.occupySeat(student, seat);
    } else {
      student.s = WAITING_FOR_SEAT;
      student.seatWaitStartTick = this.tick;
      const spot = getWaitingSpot(this.layout, this.waitingSeatQueue.length);
      student.x = spot.x;
      student.y = spot.y;
      student.target = spot;
      this.waitingSeatQueue.push(student.id);
    }
  }

  findBestSeat(student) {
    const free = this.seats.filter(s => s.occupiedBy === null);
    if (free.length === 0) return null;

    const DIAGONAL = { top: 'bottom', bottom: 'top', left: 'right', right: 'left' };
    const tableOccupancy = new Map();
    for (const s of this.seats) {
      if (!tableOccupancy.has(s.tableId)) tableOccupancy.set(s.tableId, 0);
      if (s.occupiedBy !== null) tableOccupancy.set(s.tableId, tableOccupancy.get(s.tableId) + 1);
    }

    const sx = student.x;
    const sy = student.y;
    const maxDist = Math.hypot(this.layout.width, this.layout.height);
    const seatAreaTop = this.layout.seatArea.y;
    const seatAreaH = this.layout.seatArea.h;

    const scored = [];
    for (const seat of free) {
      const dist = Math.hypot(seat.x - sx, seat.y - sy);
      const distScore = 1 - dist / maxDist;

      const rowRatio = (seat.y - seatAreaTop) / seatAreaH;
      const depthScore = 1 - rowRatio * 0.7;

      const occ = tableOccupancy.get(seat.tableId) ?? 0;
      let socialScore;
      if (occ === 0) {
        socialScore = 1.0;
      } else {
        const tableSeats = this.seats.filter(s => s.tableId === seat.tableId);
        const occupied = tableSeats.filter(s => s.occupiedBy !== null);
        const isDiag = occupied.every(s => DIAGONAL[s.position] === seat.position);
        socialScore = isDiag ? 0.7 : 0.3 - occ * 0.1;
      }

      const score = distScore * 0.35 + depthScore * 0.3 + socialScore * 0.35;
      scored.push({ seat, score });
    }

    scored.sort((a, b) => b.score - a.score);
    const topN = Math.min(scored.length, Math.max(3, Math.ceil(scored.length * 0.15)));
    const pick = Math.floor(Math.random() * topN);
    return scored[pick].seat;
  }

  findFreeSeat() {
    return this.seats.find(seat => seat.occupiedBy === null);
  }

  occupySeat(student, seat) {
    seat.occupiedBy = student.id;
    student.seatId = seat.id;
    student.s = SEEK_SEAT;
    student.target = { x: seat.x, y: seat.y };
    if (student.seatWaitStartTick !== null) {
      student.totalSeatWait = this.tick - student.seatWaitStartTick;
      this.totalSeatWaitTime += student.totalSeatWait;
      student.seatWaitStartTick = null;
    }
  }

  allocateSeatsToWaitingStudents() {
    if (this.waitingSeatQueue.length === 0) return;
    const toRemove = [];
    for (let i = 0; i < this.waitingSeatQueue.length; i++) {
      const studentId = this.waitingSeatQueue[i];
      const student = this.students.get(studentId);
      if (!student || student.s !== WAITING_FOR_SEAT) { toRemove.push(i); continue; }
      const seat = this.findBestSeat(student);
      if (!seat) break;
      this.occupySeat(student, seat);
      toRemove.push(i);
    }
    for (let i = toRemove.length - 1; i >= 0; i--) {
      this.waitingSeatQueue.splice(toRemove[i], 1);
    }

    this.waitingSeatQueue.forEach((id, index) => {
      const student = this.students.get(id);
      if (!student) return;
      const spot = getWaitingRoamingSpot(this.layout, index, this.tick);
      student.target = spot;
      moveTowards(student, spot, 1.2);
    });
  }

  updateEatingStudents() {
    for (const student of this.students.values()) {
      if (student.s !== EATING) continue;
      student.eatingLeft -= 1;
      if (student.eatingLeft <= 0) {
        const seat = this.seats.find(item => item.id === student.seatId);
        if (seat) seat.occupiedBy = null;
        this.totalEatingTime += Math.max(0, 360 - student.eatingLeft);
        student.s = LEAVING;
        student.leaveStage = 'toLane';
        student.target = {
          x: this.layout.exitLane.x + this.layout.exitLane.w / 2,
          y: Math.min(this.layout.exit.y - 24, Math.max(this.layout.exitLane.y + 18, student.y))
        };
        student.speed = randomNormal(5.2, 0.8, 3.2);
      }
    }
  }

  waitingQueueLength(win) {
    return win.queue
      .map(id => this.students.get(id))
      .filter(student => student && student.s !== ORDERING && student.s !== LEAVING).length;
  }

  currentWaitingTimeSum(win) {
    return win.queue
      .map(id => this.students.get(id))
      .filter(student => student && student.s !== ORDERING && typeof student.queueStartTick === 'number')
      .reduce((sum, student) => sum + Math.max(0, this.tick - student.queueStartTick), 0);
  }

  avgQueueTimeIncludingCurrent(win) {
    const completedTotal = win.waitSamples.reduce((sum, value) => sum + value, 0);
    const currentStudents = win.queue
      .map(id => this.students.get(id))
      .filter(student => student && student.s !== ORDERING && typeof student.queueStartTick === 'number');
    const total = completedTotal + currentStudents.reduce((sum, student) => sum + Math.max(0, this.tick - student.queueStartTick), 0);
    const count = win.waitSamples.length + currentStudents.length;
    return count ? total / count : 0;
  }

  sampleStats() {
    const activeCount = this.students.size;
    const occupiedCount = this.seats.filter(seat => seat.occupiedBy !== null).length;
    this.maxCongestion = Math.max(this.maxCongestion, activeCount);

    for (const seat of this.seats) {
      if (seat.occupiedBy !== null) seat.occupancyTicks += 1;
    }

    for (const win of this.windows) {
      win.qLenSamples.push(this.waitingQueueLength(win));
    }

    if (this.tick % 10 === 0 || this.tick === 1) {
      this.trend.push({
        tick: this.tick,
        activeCount,
        queueCount: Array.from(this.students.values()).filter(s => [QUEUEING, ORDERING].includes(s.s)).length,
        waitingSeatCount: this.waitingSeatQueue.length,
        occupiedSeats: occupiedCount
      });
    }
  }

  emitSnapshot() {
    const studentList = Array.from(this.students.values()).map(({ id, x, y, s }) => ({
      id,
      x: Number(x.toFixed(1)),
      y: Number(y.toFixed(1)),
      s
    }));

    this.handlers.onSnapshot?.({
      tick: this.tick,
      generated: this.generated,
      finished: this.finished,
      lost: this.lost,
      students: studentList,
      windows: this.windows.map(win => ({ id: win.id, dishName: win.dishName, popularityRank: win.popularityRank, popularityScore: win.popularityScore, qLen: this.waitingQueueLength(win), served: win.served, avgWaitTime: Math.round(this.avgQueueTimeIncludingCurrent(win)), peakQueueLength: win.qLenSamples.length ? Math.max(...win.qLenSamples) : this.waitingQueueLength(win) })),
      seats: this.seats.map(seat => ({ id: seat.id, x: seat.x, y: seat.y, occupied: seat.occupiedBy !== null, occupancyTicks: seat.occupancyTicks })),
      stats: {
        activeCount: studentList.length,
        occupiedSeats: this.seats.filter(seat => seat.occupiedBy !== null).length,
        waitingSeatCount: this.waitingSeatQueue.length,
        maxCongestion: this.maxCongestion,
        progress: Math.min(100, Math.round((this.tick / this.config.simDurationTick) * 100))
      }
    });
  }

  buildReport() {
    const arrived = this.generated;
    const served = this.windows.reduce((sum, win) => sum + win.served, 0);
    const currentWaitingTime = this.windows.reduce((sum, win) => sum + this.currentWaitingTimeSum(win), 0);
    const currentWaitingCount = this.windows.reduce((sum, win) => sum + this.waitingQueueLength(win), 0);
    const avgWaitTime = (served + currentWaitingCount) ? (this.totalQueueTime + currentWaitingTime) / (served + currentWaitingCount) : 0;
    const avgSeatWaitTime = served ? this.totalSeatWaitTime / served : 0;
    const lossRate = arrived ? this.lost / arrived : 0;
    const seatTurnoverRate = this.config.seatCount ? served / this.config.seatCount : 0;
    const windowPerformance = this.windows.map(win => ({
      id: win.id,
      dishName: win.dishName,
      popularityRank: win.popularityRank,
      popularityScore: win.popularityScore,
      avgQueueLength: win.qLenSamples.length ? win.qLenSamples.reduce((a, b) => a + b, 0) / win.qLenSamples.length : 0,
      avgWaitTime: this.avgQueueTimeIncludingCurrent(win),
      peakQueueLength: win.qLenSamples.length ? Math.max(...win.qLenSamples) : this.waitingQueueLength(win),
      totalServedCount: win.served
    }));

    const score = lossRate > 0.15 || avgWaitTime > 240
      ? '极度拥挤'
      : lossRate > 0.06 || avgWaitTime > 150
        ? '偏拥挤'
        : avgWaitTime > 80
          ? '基本可控'
          : '运行顺畅';

    const recommendedWindows = Math.max(0, Math.ceil((avgWaitTime - 120) / 60));
    const suggestion = recommendedWindows > 0
      ? `建议增加 ${recommendedWindows} 个窗口，或将热门菜品拆分至快餐窗口以削峰。`
      : '当前窗口配置基本可行，可重点观察座位周转与端盘等座区域。';

    return {
      simId: `LOCAL_${Date.now()}`,
      createdAt: new Date().toLocaleString(),
      config: this.config,
      score,
      suggestion,
      summary: {
        avgWaitTime,
        avgSeatWaitTime,
        seatTurnoverRate,
        lossRate,
        maxCongestion: this.maxCongestion,
        generated: this.generated,
        finished: this.finished,
        lost: this.lost,
        served
      },
      trend: this.trend,
      windowPerformance
    };
  }
}
