import { STUDENT_STATES } from '../utils/constants';
import { getQueueSpot } from '../utils/layout';

const { PATHFINDING, QUEUEING, ORDERING, WAITING_FOR_SEAT, SEEK_SEAT, EATING, LEAVING } = STUDENT_STATES;

/**
 * 回放后端仿真事件流。
 * 布局数据（窗口/座位位置、入口/出口坐标）直接使用后端响应中的 serverData.layout，
 * 无需在前端重新生成——前端已将同一份布局发送给后端，后端原样返回并附加了菜品信息。
 */
export class EventPlayer {
  constructor(serverData, config, handlers = {}) {
    this.config = config;
    this.handlers = handlers;
    this.totalTicks = serverData.totalTicks || config.simDurationTick || 3600;
    this.report = serverData.report || null;

    const layout = serverData.layout || {};
    this.layout = {
      entrance: layout.entrance || { x: 70, y: 852 },
      exit:     layout.exit     || { x: 1530, y: 852 },
      exitLane: layout.exitLane || { x: 1430, y: 300, w: 44, h: 500 },
      windows:  (layout.windows || []).map(w => ({
        ...w,
        queueX:      w.queueX      ?? w.x,
        queueStartY: w.queueStartY ?? (w.y + 55),
        queueGap:    w.queueGap    ?? 12
      })),
      seats:    layout.seats    || []
    };

    this.renderLayout = layout.width ? {
      width:       layout.width,
      height:      layout.height,
      entrance:    this.layout.entrance,
      exit:        this.layout.exit,
      serviceArea: layout.serviceArea,
      seatArea:    layout.seatArea,
      waitingZone: layout.waitingZone,
      exitLane:    layout.exitLane,
      windows:     this.layout.windows,
      tables:      layout.tables  || [],
      seats:       this.layout.seats
    } : null;

    this.windowMap = new Map();
    for (const w of this.layout.windows) this.windowMap.set(w.id, w);

    this.timelines = new Map();
    this.lostByTick = [];
    this.buildTimelines(serverData.events || []);

    this.currentTick = 0;
    this.rafId = null;
    this.lastFrameTime = 0;
    this.speedFactor = 1;
    this.paused = false;

    this.seatHeatIndex = new Map();
    for (const s of this.layout.seats) this.seatHeatIndex.set(s.id, 0);
    this.lastEmittedTick = 0;

    this.windowStats = new Map();
    for (const w of this.layout.windows) {
      this.windowStats.set(w.id, {
        id: w.id,
        dishName:        w.dishName        || w.id,
        popularityRank:  w.popularityRank  || 99,
        popularityScore: w.popularityScore || 1,
        qLen: 0, served: 0, peakQueueLength: 0,
        avgWaitTime: 0,
        _totalWait: 0,
        _waitCount: 0
      });
    }

    this.queueCompletions = [];
    this.seatWaitCompletions = [];
    this.seatAbandonTicks = [];
    this.buildMetrics(serverData.events || []);
  }

  buildMetrics(events) {
    const studentEvents = new Map();
    for (const evt of events) {
      if (evt.type === 'LOST') continue;
      if (!studentEvents.has(evt.studentId)) studentEvents.set(evt.studentId, []);
      studentEvents.get(evt.studentId).push(evt);
    }
    for (const [, evts] of studentEvents) {
      let queueTick = null;
      let waitSeatTick = null;
      let currentWindowId = null;
      for (const evt of evts) {
        if (evt.type === 'ARRIVE') currentWindowId = evt.targetId;
        if (evt.type === 'QUEUE') {
          queueTick = evt.tick;
          if (evt.targetId) currentWindowId = evt.targetId;
        }
        if (evt.type === 'ORDER_START' && queueTick !== null) {
          const winId = evt.targetId || currentWindowId;
          this.queueCompletions.push({ tick: evt.tick, waitTime: evt.tick - queueTick, windowId: winId });
          queueTick = null;
        }
        if (evt.type === 'WAIT_SEAT') waitSeatTick = evt.tick;
        if (evt.type === 'SIT' && waitSeatTick !== null) {
          this.seatWaitCompletions.push({ tick: evt.tick, waitTime: evt.tick - waitSeatTick });
          waitSeatTick = null;
        }
        if (evt.type === 'SEAT_ABANDON') this.seatAbandonTicks.push(evt.tick);
      }
    }
    this.queueCompletions.sort((a, b) => a.tick - b.tick);
    this.seatWaitCompletions.sort((a, b) => a.tick - b.tick);
    this.seatAbandonTicks.sort((a, b) => a - b);
  }

  getRenderLayout() {
    return this.renderLayout;
  }

  buildTimelines(events) {
    const perStudent = new Map();
    for (const evt of events) {
      if (evt.type === 'LOST') {
        this.lostByTick.push(evt.tick);
        continue;
      }
      if (!perStudent.has(evt.studentId)) perStudent.set(evt.studentId, []);
      perStudent.get(evt.studentId).push(evt);
    }

    // ── Pre-pass: compute queue index for each student at QUEUE time ──
    const queueJoinIndex = new Map();
    const winQueueSize = new Map();
    const studentWinMap = new Map();
    for (const w of this.layout.windows) winQueueSize.set(w.id, 0);
    for (const evt of events) {
      if (evt.type === 'ARRIVE') {
        studentWinMap.set(evt.studentId, evt.targetId);
      } else if (evt.type === 'QUEUE') {
        const wid = studentWinMap.get(evt.studentId);
        if (wid != null) {
          queueJoinIndex.set(evt.studentId, winQueueSize.get(wid) || 0);
          winQueueSize.set(wid, (winQueueSize.get(wid) || 0) + 1);
        }
      } else if (evt.type === 'ORDER_START') {
        const wid = studentWinMap.get(evt.studentId);
        if (wid != null) winQueueSize.set(wid, Math.max(0, (winQueueSize.get(wid) || 0) - 1));
      }
    }

    const exitLane = this.layout.exitLane;

    for (const [studentId, evts] of perStudent) {
      const waypoints = [];
      let windowId = null;

      for (let ei = 0; ei < evts.length; ei++) {
        const evt = evts[ei];
        const nextEvt = ei + 1 < evts.length ? evts[ei + 1] : null;

        switch (evt.type) {
          case 'ARRIVE':
            windowId = evt.targetId;
            waypoints.push({ tick: evt.tick, x: this.layout.entrance.x, y: this.layout.entrance.y, state: PATHFINDING, windowId });
            break;

          case 'QUEUE': {
            const win = this.windowMap.get(windowId);
            if (win) {
              const idx = queueJoinIndex.get(studentId) ?? 0;
              const spot = getQueueSpot(win, idx);
              waypoints.push({ tick: evt.tick, x: spot.x, y: spot.y, state: QUEUEING, windowId, isQueue: true });
            }
            break;
          }

          case 'ORDER_START': {
            const win = this.windowMap.get(windowId);
            const x = win ? win.x : evt.x;
            const y = win ? win.y + 28 : evt.y;
            const prevWp = waypoints.at(-1);
            if (prevWp && prevWp.state === QUEUEING && prevWp.tick < evt.tick && win) {
              const walkTicks = 6;
              const walkStart = Math.max(prevWp.tick + 1, evt.tick - walkTicks);
              const front = getQueueSpot(win, 0);
              waypoints.push({ tick: walkStart, x: front.x, y: front.y, state: QUEUEING, windowId, isQueue: true });
              waypoints.push({ tick: walkStart, x: front.x, y: front.y, state: PATHFINDING, windowId });
            }
            waypoints.push({ tick: evt.tick, x, y, state: ORDERING, windowId });
            break;
          }

          case 'ORDER_END': {
            const prevWp = waypoints.at(-1);
            const ox = prevWp?.x ?? evt.x;
            const oy = prevWp?.y ?? evt.y;
            const orderDur = evt.tick - (prevWp?.tick ?? evt.tick);
            const earlyDep = Math.min(4, Math.max(0, orderDur - 2));
            const depTick = evt.tick - earlyDep;
            const gap = nextEvt ? nextEvt.tick - depTick - 1 : 12;
            const stepTicks = Math.max(3, Math.min(10, gap));
            const sideTicks = Math.max(1, Math.floor(stepTicks / 3));
            waypoints.push({ tick: depTick, x: ox, y: oy, state: SEEK_SEAT, windowId });
            waypoints.push({ tick: depTick + sideTicks, x: ox + 30, y: oy + 15, state: SEEK_SEAT, windowId });
            waypoints.push({ tick: depTick + stepTicks, x: ox + 30, y: oy + 80, state: SEEK_SEAT, windowId });
            break;
          }

          case 'SIT': {
            const prevWp = waypoints.at(-1);
            if (prevWp && prevWp.state === WAITING_FOR_SEAT && prevWp.tick < evt.tick) {
              // 直线走向座位，固定 5 px/tick 速度（与离场速度一致）
              const SEEK_SPEED = 5;
              const dist = Math.hypot(evt.x - prevWp.x, evt.y - prevWp.y);
              const walkTicks = Math.max(2, Math.ceil(dist / SEEK_SPEED));
              const maxGap = evt.tick - prevWp.tick - 1;
              const useTicks = Math.min(walkTicks, Math.max(1, maxGap));
              const startTick = evt.tick - useTicks;
              waypoints.push({ tick: startTick, x: prevWp.x, y: prevWp.y, state: SEEK_SEAT });
            }
            waypoints.push({ tick: evt.tick, x: evt.x, y: evt.y, state: EATING, seatId: evt.targetId });
            break;
          }

          case 'WAIT_SEAT': {
            const win = windowId ? this.windowMap.get(windowId) : null;
            if (win) {
              // 等座位置：窗口右下方 service area 范围内（避开座位区和队伍）
              const h = Math.abs(hashCode(studentId));
              const ox = win.queueX + 22 + (h % 28);            // 队列右侧 22-50px
              const oy = (win.y || 100) + 50 + ((h >> 5) % 160); // 窗口下方 50-210px，仍在 service area
              waypoints.push({ tick: evt.tick, x: ox, y: oy, state: WAITING_FOR_SEAT });
            } else {
              const h = Math.abs(hashCode(studentId));
              waypoints.push({ tick: evt.tick, x: 100 + (h % 80), y: 200 + ((h >> 6) % 80), state: WAITING_FOR_SEAT });
            }
            break;
          }

          case 'SEAT_ABANDON':
            this._addLeavePath(waypoints, evt.tick, evt.x, evt.y, exitLane, null);
            break;

          case 'LEAVE': {
            const exitEvt = evts.slice(ei + 1).find(e => e.type === 'EXIT');
            this._addLeavePath(waypoints, evt.tick, evt.x, evt.y, exitLane, exitEvt);
            break;
          }

          case 'EXIT': {
            const last = waypoints.at(-1);
            if (!last || last.state !== '_GONE') {
              waypoints.push({ tick: evt.tick, x: this.layout.exit.x, y: this.layout.exit.y, state: '_GONE' });
            }
            break;
          }
        }
      }
      if (waypoints.length > 0) {
        this.timelines.set(studentId, { waypoints, windowId });
      }
    }

    for (const [, tl] of this.timelines) {
      const last = tl.waypoints.at(-1);
      if (!last || last.state === '_GONE') continue;
      tl.waypoints.push({ tick: this.totalTicks, x: last.x, y: last.y, state: '_GONE' });
    }
  }

  _addLeavePath(waypoints, startTick, startX, startY, exitLane, exitEvt) {
    const laneX = exitLane.x + exitLane.w / 2;
    const laneY = Math.min(this.layout.exit.y - 24, Math.max(exitLane.y + 18, startY));
    const dist1 = Math.hypot(laneX - startX, laneY - startY);
    const dist2 = Math.hypot(this.layout.exit.x - laneX, this.layout.exit.y - laneY);
    const totalDist = dist1 + dist2;

    let t1, endTick;
    if (exitEvt && exitEvt.tick > startTick) {
      const total = exitEvt.tick - startTick;
      t1 = totalDist > 0 ? Math.max(1, Math.round(total * dist1 / totalDist)) : Math.round(total / 2);
      endTick = exitEvt.tick;
    } else {
      t1 = Math.max(5, Math.round(dist1 / 5));
      const t2 = Math.max(5, Math.round(dist2 / 5));
      endTick = startTick + t1 + t2;
    }

    waypoints.push({ tick: startTick, x: startX, y: startY, state: LEAVING });
    waypoints.push({ tick: startTick + t1, x: laneX, y: laneY, state: LEAVING });
    waypoints.push({ tick: endTick, x: this.layout.exit.x, y: this.layout.exit.y, state: '_GONE' });
  }

  start() {
    this.lastFrameTime = performance.now();
    this.emitFrame();
    this.loop = this._tick.bind(this);
    this.rafId = requestAnimationFrame(this.loop);
  }

  stop() {
    if (this.rafId) cancelAnimationFrame(this.rafId);
    this.rafId = null;
  }

  setPaused(p) { this.paused = p; }

  setSpeedFactor(f) {
    this.speedFactor = Math.max(0.5, Math.min(8, Number(f) || 1));
  }

  _tick(now) {
    if (!this.paused) {
      const dt = (now - this.lastFrameTime) / 1000;
      this.currentTick += dt * 10 * this.speedFactor;
      if (this.currentTick >= this.totalTicks) {
        this.currentTick = this.totalTicks;
        this.emitFrame();
        this.stop();
        this.handlers.onFinish?.(this.report);
        return;
      }
      this.emitFrame();
    }
    this.lastFrameTime = now;
    this.rafId = requestAnimationFrame(this.loop);
  }

  emitFrame() {
    const t = this.currentTick;

    const tickDelta = Math.max(0, Math.floor(t) - this.lastEmittedTick);
    this.lastEmittedTick = Math.floor(t);

    const windowQueues  = new Map();
    const seatOccupancy = new Map();
    for (const w of this.layout.windows) windowQueues.set(w.id, []);

    for (const [sid, tl] of this.timelines) {
      const s = this._currentState(tl, t);
      if (!s) continue;
      // 只有已到达排队位置（QUEUEING）的学生才计入窗口队列
      // PATHFINDING（仍在走向窗口）和 ORDERING（正在打饭）不参与队列计数和视觉排位
      if (s === QUEUEING && tl.windowId) {
        const q = windowQueues.get(tl.windowId);
        if (q) q.push(sid);
      }
    }

    const students = [];
    for (const [sid, tl] of this.timelines) {
      const pos = this._interpolate(sid, tl, t, windowQueues);
      if (pos) students.push({ id: sid, x: Math.round(pos.x * 10) / 10, y: Math.round(pos.y * 10) / 10, s: pos.s });
      if (this._currentState(tl, t) === EATING) {
        const seatId = this._currentSeatId(tl, t);
        if (seatId) seatOccupancy.set(seatId, sid);
      }
    }

    if (tickDelta > 0) {
      const decay = Math.pow(0.99, tickDelta);
      for (const [seatId] of this.seatHeatIndex) {
        const prev = this.seatHeatIndex.get(seatId);
        if (seatOccupancy.has(seatId)) {
          this.seatHeatIndex.set(seatId, prev * decay + (1 - decay));
        } else {
          this.seatHeatIndex.set(seatId, prev * decay);
        }
      }
    }

    let generated = 0, finished = 0;
    for (const [, tl] of this.timelines) {
      if (tl.waypoints[0].tick <= t) generated++;
      const last = tl.waypoints.at(-1);
      if (last.state === '_GONE' && t >= last.tick) finished++;
    }
    let lost = 0;
    for (const lt of this.lostByTick) { if (lt <= t) lost++; }
    generated += lost;

    for (const [, ws] of this.windowStats) { ws.qLen = 0; ws.served = 0; }
    for (const [winId, q] of windowQueues) {
      const ws = this.windowStats.get(winId);
      if (ws) { ws.qLen = q.length; ws.peakQueueLength = Math.max(ws.peakQueueLength, ws.qLen); }
    }
    for (const [, tl] of this.timelines) {
      if (!tl.windowId) continue;
      for (const wp of tl.waypoints) {
        if (wp.state === SEEK_SEAT && wp.tick <= t) {
          const ws = this.windowStats.get(tl.windowId);
          if (ws) ws.served++;
          break;
        }
      }
    }

    let totalQueueWait = 0, queueCount = 0;
    for (const [, ws] of this.windowStats) { ws._totalWait = 0; ws._waitCount = 0; }
    for (const c of this.queueCompletions) {
      if (c.tick > t) break;
      totalQueueWait += c.waitTime;
      queueCount++;
      if (c.windowId) {
        const ws = this.windowStats.get(c.windowId);
        if (ws) { ws._totalWait += c.waitTime; ws._waitCount++; }
      }
    }
    for (const [, ws] of this.windowStats) {
      ws.avgWaitTime = ws._waitCount > 0 ? ws._totalWait / ws._waitCount : 0;
    }
    let totalSeatWait = 0, seatWaitCount = 0;
    for (const c of this.seatWaitCompletions) {
      if (c.tick > t) break;
      totalSeatWait += c.waitTime;
      seatWaitCount++;
    }
    let seatAbandoned = 0;
    for (const at of this.seatAbandonTicks) {
      if (at > t) break;
      seatAbandoned++;
    }

    this.handlers.onSnapshot?.({
      tick: Math.floor(t),
      generated, finished, lost,
      students,
      windows: Array.from(this.windowStats.values()),
      seats: this.layout.seats.map(seat => ({
        id: seat.id, x: seat.x, y: seat.y,
        occupied: seatOccupancy.has(seat.id),
        heatIndex: this.seatHeatIndex.get(seat.id) || 0
      })),
      stats: {
        activeCount:      students.length,
        occupiedSeats:    seatOccupancy.size,
        waitingSeatCount: students.filter(s => s.s === WAITING_FOR_SEAT).length,
        avgWaitTime:      queueCount > 0 ? totalQueueWait / queueCount : 0,
        avgSeatWaitTime:  seatWaitCount > 0 ? totalSeatWait / seatWaitCount : 0,
        seatAbandoned,
        maxCongestion:    0,
        progress:         Math.min(100, Math.round((t / this.totalTicks) * 100))
      }
    });
  }

  _currentState(tl, t) {
    const wp = tl.waypoints;
    if (t < wp[0].tick) return null;
    const last = wp.at(-1);
    if ((last.state === '_GONE' || last.state === '_LEAVE_TARGET') && t >= last.tick) return null;
    let s = wp[0].state;
    for (let i = 1; i < wp.length; i++) {
      if (wp[i].tick <= t) s = wp[i].state; else break;
    }
    if (s === '_GONE' || s === '_LEAVE_TARGET') return null;
    return s;
  }

  _currentSeatId(tl, t) {
    let seatId = null;
    for (const wp of tl.waypoints) {
      if (wp.tick > t) break;
      if (wp.seatId) seatId = wp.seatId;
    }
    return seatId;
  }

  _interpolate(sid, tl, t, windowQueues) {
    const wp = tl.waypoints;
    if (t < wp[0].tick) return null;
    const last = wp.at(-1);
    if ((last.state === '_GONE' || last.state === '_LEAVE_TARGET') && t >= last.tick) return null;

    let pi = 0;
    for (let i = 1; i < wp.length; i++) {
      if (wp[i].tick <= t) pi = i; else break;
    }

    const prev = wp[pi];
    const next = pi + 1 < wp.length ? wp[pi + 1] : null;

    let px = prev.x, py = prev.y;
    if (prev.isQueue && tl.windowId) {
      const win = this.windowMap.get(tl.windowId);
      const q   = windowQueues.get(tl.windowId) || [];
      const idx = q.indexOf(sid);
      if (win && idx >= 0) {
        const spot = getQueueSpot(win, idx);
        px = spot.x; py = spot.y;
      }
    }

    if (prev.state === '_GONE' || prev.state === '_LEAVE_TARGET') return null;
    const state = prev.state;

    if (state === QUEUEING || state === ORDERING || state === EATING || state === WAITING_FOR_SEAT) {
      return { x: px, y: py, s: state };
    }

    if (next && next.tick > prev.tick) {
      let nx = next.x, ny = next.y;
      if (next.isQueue && tl.windowId) {
        const win = this.windowMap.get(tl.windowId);
        const q   = windowQueues.get(tl.windowId) || [];
        const idx = q.indexOf(sid);
        if (win && idx >= 0) {
          const spot = getQueueSpot(win, idx);
          nx = spot.x; ny = spot.y;
        }
      }
      const frac = (t - prev.tick) / (next.tick - prev.tick);
      return { x: px + (nx - px) * frac, y: py + (ny - py) * frac, s: state };
    }

    return { x: px, y: py, s: state };
  }
}

function hashCode(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) | 0;
  return h;
}
