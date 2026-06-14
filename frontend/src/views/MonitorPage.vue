<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import CanvasLegend from '../components/CanvasLegend.vue';
import MetricCard from '../components/MetricCard.vue';
import { LocalDiningSimulator } from '../services/localSimulator';
import { EventPlayer } from '../services/eventPlayer';
import { renderCanteen } from '../composables/useCanvasRenderer';
import { createCanteenLayout } from '../utils/layout';

const props = defineProps({
  config:           { type: Object,  required: true },
  useLocalFallback: { type: Boolean, default: true },
  serverData:       { type: Object,  default: null }
});

const emit = defineEmits(['finished', 'restart']);

const canvasRef = ref(null);
const snapshot = ref(null);
const paused = ref(false);
const speedFactor = ref(1);
const heatmapOn = ref(false);
const connectionStatus = ref('准备中');
const reportRequesting = ref(false);
const serverReport = ref(null);
const backendLayout = ref(null);
const runningMode = ref(props.useLocalFallback ? '本地演示' : '后端事件流');
const trendHistory = ref([]);
const arrivalSamples = ref([]);
const fullArrivalSamples = ref([]);
const windowSamples = ref(new Map());
const speedOptions = [0.5, 1, 2, 4, 8];
const windowTooltip = ref(null);
const hoveredWindowId = ref(null);

let simulator = null;   // LocalDiningSimulator instance
let player = null;      // EventPlayer instance
let renderRaf = 0;
let lastGeneratedCount = 0;
let pendingArrivalCount = 0;
let lastArrivalSampleTick = -1;
const ARRIVAL_SAMPLE_INTERVAL = 10;

// ── Computed ──────────────────────────────────────────────────────────────
const activeCount     = computed(() => snapshot.value?.stats?.activeCount ?? snapshot.value?.students?.length ?? 0);
const occupiedSeats   = computed(() => snapshot.value?.stats?.occupiedSeats ?? 0);
const waitingSeatCount= computed(() => snapshot.value?.stats?.waitingSeatCount ?? 0);

const avgWaitTime     = computed(() => snapshot.value?.stats?.avgWaitTime ?? 0);
const avgSeatWaitTime = computed(() => snapshot.value?.stats?.avgSeatWaitTime ?? 0);

function formatTickDuration(value) {
  const totalSeconds = Math.round(Number(value || 0));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes <= 0) return `${seconds}秒`;
  return `${minutes}分${String(seconds).padStart(2, '0')}秒`;
}
const avgWaitDisplay     = computed(() => formatTickDuration(avgWaitTime.value));
const avgSeatWaitDisplay = computed(() => formatTickDuration(avgSeatWaitTime.value));
const seatAbandoned  = computed(() => snapshot.value?.stats?.seatAbandoned ?? 0);
const bottleneckType = computed(() => snapshot.value?.stats?.bottleneckType ?? '运行监测中');
const progress       = computed(() =>
  snapshot.value?.stats?.progress ??
  Math.min(100, Math.round(((snapshot.value?.tick ?? 0) / props.config.simDurationTick) * 100))
);
const lossRate = computed(() => {
  const generated = snapshot.value?.generated ?? 0;
  const lost      = snapshot.value?.lost ?? 0;
  return generated ? ((lost / generated) * 100).toFixed(1) : '0.0';
});
const renderMode = computed(() =>
  activeCount.value > (props.config.renderThreshold ?? 300) ? '圆点' : '人物'
);
const hotWindows = computed(() => (snapshot.value?.windows ?? [])
  .filter(win => win.popularityRank && win.popularityRank <= 6)
  .sort((a, b) => a.popularityRank - b.popularityRank)
  .slice(0, 6));

const arrivalSeries   = computed(() => arrivalSamples.value.slice(-64));
const latestArrival   = computed(() => arrivalSeries.value.at(-1)?.arrivals ?? 0);
const peakArrival     = computed(() => arrivalSeries.value.reduce((max, item) => Math.max(max, item.arrivals), 0));
const arrivalPolyline = computed(() => {
  const series = arrivalSeries.value;
  if (series.length < 2) return '';
  const width = 220, height = 64, pad = 6;
  const max = Math.max(1, ...series.map(item => item.arrivals));
  return series.map((item, index) => {
    const x = pad + (index / Math.max(1, series.length - 1)) * (width - pad * 2);
    const y = height - pad - (item.arrivals / max) * (height - pad * 2);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
});


function calculateReportScore({ lossRate, avgWaitTime, avgSeatWaitTime, maxSeatWaiting, maxCongestion, windowPerformance }) {
  const queueScore = avgWaitTime <= 300 ? 30 : avgWaitTime <= 600 ? 25 : avgWaitTime <= 900 ? 20 : avgWaitTime <= 1500 ? 14 : 8;
  const lossScore = lossRate <= 0.05 ? 25 : lossRate <= 0.10 ? 20 : lossRate <= 0.18 ? 15 : lossRate <= 0.30 ? 8 : 3;
  const seatScore = (avgSeatWaitTime <= 180 && maxSeatWaiting <= 30) ? 20
    : (avgSeatWaitTime <= 420 && maxSeatWaiting <= 60) ? 16
      : (avgSeatWaitTime <= 780 && maxSeatWaiting <= 100) ? 10
        : (avgSeatWaitTime <= 1200 && maxSeatWaiting <= 140) ? 6 : 2;
  const avgQueue = windowPerformance.length ? windowPerformance.reduce((sum, item) => sum + (item.avgQueueLength || 0), 0) / windowPerformance.length : 0;
  const maxQueue = windowPerformance.length ? Math.max(...windowPerformance.map(item => item.avgQueueLength || 0)) : 0;
  const balanceRatio = avgQueue <= 0.1 ? 1 : maxQueue / Math.max(0.1, avgQueue);
  const balanceScore = balanceRatio <= 1.3 ? 15 : balanceRatio <= 1.6 ? 12 : balanceRatio <= 2.0 ? 8 : balanceRatio <= 2.5 ? 5 : 2;
  const rawCapacity = Math.max(1, (props.config.seatCount || 0) + (props.config.windowCount || 0) * Math.max(1, props.config.maxQueueCapacity || 30));
  const capacity = Math.max(1, Math.round(rawCapacity * 1.35));
  const pressure = (maxCongestion || 0) / capacity;
  const pressureScore = pressure <= 0.85 ? 10 : pressure <= 1.05 ? 8 : pressure <= 1.25 ? 6 : pressure <= 1.50 ? 4 : 2;
  const numericScore = Math.max(0, Math.min(100, queueScore + lossScore + seatScore + balanceScore + pressureScore));
  const gradeLevel = numericScore >= 90 ? '优秀' : numericScore >= 80 ? '良好' : numericScore >= 70 ? '基本可控' : numericScore >= 60 ? '偏拥挤' : '严重拥挤';
  const deductions = [];
  if (queueScore < 20) deductions.push('平均排队时长偏长');
  if (lossScore < 15) deductions.push('窗口流失率偏高');
  if (seatScore < 16) deductions.push('找座等待压力偏高');
  if (balanceScore < 12) deductions.push('热门窗口负载不均衡');
  if (pressureScore < 6) deductions.push('高峰在场人数接近承载上限');
  return { numericScore, gradeLevel, deductionReason: deductions.length ? deductions.join('；') : '各项指标较均衡，未出现明显短板。' };
}

// ── Rendering ──────────────────────────────────────────────────────────────
function scheduleRender(data) {
  if (renderRaf) cancelAnimationFrame(renderRaf);
  renderRaf = requestAnimationFrame(() => {
    renderCanteen(canvasRef.value, data, props.config, { heatmap: heatmapOn.value });
    renderRaf = 0;
  });
}

// ── Snapshot handler (shared by both local and EventPlayer) ────────────────
function handleSnapshot(data) {
  if (data?.type === 'CONTROL_ACK') {
    if (data.command === 'SPEED') speedFactor.value = data.speedFactor ?? speedFactor.value;
    return;
  }
  if (data?.type === 'ERROR') {
    connectionStatus.value = data.message || '错误';
    reportRequesting.value = false;
    return;
  }

  const normalized = normalizeSnapshot(data);
  snapshot.value = normalized;
  collectHistory(normalized);
  refreshWindowTooltip();
  nextTick(() => scheduleRender(normalized));

  if (data?.type === 'FINISHED' || data?.finished === true || data?.done === true) {
    finish(data.report || buildPartialReport());
  }
}

function normalizeSnapshot(data) {
  return {
    tick:     data.tick ?? 0,
    generated:data.generated ?? data.stats?.generated ?? 0,
    finished: data.finishedCount ?? data.finished ?? data.stats?.finished ?? 0,
    lost:     data.lost ?? data.lossCount ?? data.stats?.lost ?? 0,
    students: data.students ?? [],
    windows:  data.windows ?? [],
    seats:    data.seats ?? [],
    stats: {
      activeCount:      data.stats?.activeCount ?? data.students?.length ?? 0,
      occupiedSeats:    data.stats?.occupiedSeats ?? 0,
      waitingSeatCount: data.stats?.waitingSeatCount ?? data.waitingSeatCount ?? 0,
      avgWaitTime:      data.stats?.avgWaitTime ?? 0,
      avgSeatWaitTime:  data.stats?.avgSeatWaitTime ?? 0,
      maxCongestion:    data.stats?.maxCongestion ?? 0,
      seatAbandoned:    data.stats?.seatAbandoned ?? data.seatAbandoned ?? 0,
      progress:         data.stats?.progress ?? Math.min(100, Math.round(((data.tick ?? 0) / props.config.simDurationTick) * 100)),
      bottleneckType:   data.stats?.bottleneckType ?? '运行监测中',
    }
  };
}

function collectHistory(data) {
  if (!data) return;
  const currentGenerated = data.generated ?? lastGeneratedCount;
  const deltaArrivals = Math.max(0, currentGenerated - lastGeneratedCount);
  lastGeneratedCount = currentGenerated;
  pendingArrivalCount += deltaArrivals;

  const currentTick = data.tick ?? 0;
  const shouldSample = lastArrivalSampleTick < 0
    || currentTick - lastArrivalSampleTick >= ARRIVAL_SAMPLE_INTERVAL
    || data.finished === true || data.type === 'FINISHED';

  if (shouldSample) {
    trendHistory.value.push({
      tick: currentTick,
      activeCount:      data.stats?.activeCount ?? 0,
      occupiedSeats:    data.stats?.occupiedSeats ?? 0,
      waitingSeatCount: data.stats?.waitingSeatCount ?? 0,
      generated: currentGenerated
    });
    const arrivalPoint = { tick: currentTick, arrivals: pendingArrivalCount };
    arrivalSamples.value.push(arrivalPoint);
    fullArrivalSamples.value.push(arrivalPoint);
    pendingArrivalCount = 0;
    lastArrivalSampleTick = currentTick;
    if (arrivalSamples.value.length > 120) arrivalSamples.value.shift();
  }
  for (const win of data.windows || []) {
    if (!windowSamples.value.has(win.id)) windowSamples.value.set(win.id, []);
    windowSamples.value.get(win.id).push(win.qLen ?? 0);
  }
}

// ── Mode: Local simulation ────────────────────────────────────────────────
function startLocal() {
  runningMode.value = '本地演示';
  connectionStatus.value = '运行中';
  simulator = new LocalDiningSimulator(props.config, {
    onSnapshot: handleSnapshot,
    onFinish: finish
  });
  simulator.start();
}

// ── Mode: Backend event-stream playback (serverData 已在 ConfigPage 拿到) ──
function startBackend() {
  runningMode.value = '后端事件流';
  connectionStatus.value = '回放中';
  serverReport.value = props.serverData.report || null;
  player = new EventPlayer(props.serverData, props.config, {
    onSnapshot: (data) => {
      snapshot.value = data;
      collectHistory(data);
      refreshWindowTooltip();
      renderCanteen(canvasRef.value, data, props.config, {
        heatmap: heatmapOn.value,
        layout: backendLayout.value
      });
    },
    onFinish: (report) => {
      connectionStatus.value = '已完成';
      finish(report || serverReport.value);
    }
  });
  backendLayout.value = player.getRenderLayout();
  player.start();
}

// ── Controls ──────────────────────────────────────────────────────────────
function togglePause() {
  paused.value = !paused.value;
  simulator?.setPaused(paused.value);
  player?.setPaused(paused.value);
}

function changeSpeed(factor) {
  speedFactor.value = factor;
  simulator?.setSpeedFactor(factor);
  player?.setSpeedFactor(factor);
}

function generateCurrentReport() {
  paused.value = true;
  simulator?.setPaused(true);
  player?.setPaused(true);
  // 总是基于当前 snapshot/trendHistory 构造阶段性报告，不再返回后端的最终报告
  finish(buildPartialReport());
}

function finish(report) {
  cleanup();
  const r = report ?? buildPartialReport();
  // 补充到达样本，让报告页可以绘制到达曲线
  if (!r.arrivalTrend && fullArrivalSamples.value.length > 0) {
    r.arrivalTrend = [...fullArrivalSamples.value];
  }
  emit('finished', r);
}

function buildPartialReport() {
  // 本地模式直接复用 simulator 内部完整状态
  if (simulator) {
    return simulator.buildReport({ reportType: '阶段性报告' });
  }
  // 后端回放模式：基于 snapshot + trendHistory + windowSamples 重建报告
  const snap = snapshot.value || {};
  const stats = snap.stats || {};
  const tick = snap.tick ?? 0;
  const generated = snap.generated ?? 0;
  const lost = snap.lost ?? 0;
  const finished = snap.finished ?? 0;
  const served = (snap.windows || []).reduce((s, w) => s + (w.served ?? 0), 0);
  const lossRate = generated > 0 ? lost / generated : 0;
  const avgWaitTime = stats.avgWaitTime ?? 0;
  const avgSeatWaitTime = stats.avgSeatWaitTime ?? 0;
  const seatTurnoverRate = props.config.seatCount ? served / props.config.seatCount : 0;
  const maxCongestion = trendHistory.value.reduce((max, item) => Math.max(max, item.activeCount ?? 0), 0);
  const maxSeatWaiting = trendHistory.value.reduce((max, item) => Math.max(max, item.waitingSeatCount ?? 0), 0);

  const windowPerformance = (snap.windows || []).map(win => {
    const samples = windowSamples.value.get(win.id) || [];
    const avgQueueLength = samples.length ? samples.reduce((a, b) => a + b, 0) / samples.length : (win.qLen ?? 0);
    const peakQueueLength = samples.length ? Math.max(...samples) : (win.peakQueueLength ?? win.qLen ?? 0);
    return {
      id: win.id,
      dishName: win.dishName ?? win.id,
      popularityRank: win.popularityRank ?? 99,
      popularityScore: win.popularityScore ?? 0,
      avgQueueLength: Number(avgQueueLength.toFixed(2)),
      avgWaitTime: win.avgWaitTime ?? 0,
      peakQueueLength,
      totalServedCount: win.served ?? 0
    };
  });

  const scoreInfo = calculateReportScore({ lossRate, avgWaitTime, avgSeatWaitTime, maxSeatWaiting, maxCongestion, windowPerformance });

  const windowPressure = lossRate > 0.05 || avgWaitTime > 180;
  const seatPressure = avgSeatWaitTime > 120 || maxSeatWaiting > 30;
  const bottleneckType = (windowPressure && seatPressure) ? '窗口与座位双重瓶颈'
    : windowPressure ? '窗口服务瓶颈'
    : seatPressure ? '座位资源瓶颈'
    : '运行基本均衡';

  const formatDur = (sec) => {
    const total = Math.round(sec || 0);
    const m = Math.floor(total / 60);
    const s = total % 60;
    return m > 0 ? `${m}分${String(s).padStart(2, '0')}秒` : `${s}秒`;
  };

  const bottleneckReason = bottleneckType === '窗口服务瓶颈'
    ? `平均排队约 ${formatDur(avgWaitTime)}，窗口流失率 ${(lossRate * 100).toFixed(1)}%，学生主要卡在打饭窗口。`
    : bottleneckType === '座位资源瓶颈'
      ? `平均找座约 ${formatDur(avgSeatWaitTime)}，找座峰值 ${maxSeatWaiting} 人，打完饭后找座是主要压力。`
      : bottleneckType === '窗口与座位双重瓶颈'
        ? `平均排队 ${formatDur(avgWaitTime)}、平均找座 ${formatDur(avgSeatWaitTime)}，窗口与座位均承压。`
        : '排队、找座、流失三项指标都处于较低区间，当前资源配置基本均衡。';

  const suggestions = [`当前主要瓶颈：${bottleneckType}。`];
  if (lossRate > 0.15) suggestions.push(`窗口流失率达 ${(lossRate * 100).toFixed(1)}%，窗口承载严重不足，建议增加 2—3 个开放窗口。`);
  else if (lossRate > 0.05 || avgWaitTime > 240) suggestions.push('排队压力偏高，建议增加 1—2 个窗口或优化窗口分流引导。');
  else if (avgWaitTime > 120) suggestions.push('排队时间略长，可在高峰时段临时增开窗口。');
  if (avgSeatWaitTime > 180 || maxSeatWaiting > 60) suggestions.push('座位资源不足，建议增加座位或引导错峰就餐。');
  else if (avgSeatWaitTime > 90 || maxSeatWaiting > 30) suggestions.push('找座时间偏长，建议提高翻台效率或在高峰期增设临时餐位。');
  if (suggestions.length === 1) suggestions.push('当前窗口和座位配置基本合理，可保留本次数据用于多轮方案对比。');

  return {
    simId: `PARTIAL_${Date.now()}`,
    createdAt: new Date().toLocaleString(),
    config: props.config,
    source: 'backend',
    reportType: '阶段性报告',
    reportNote: `本报告在仿真回放到第 ${tick} 秒（Tick）时生成，统计数据为当前阶段累计结果。`,
    score: `${scoreInfo.numericScore}分 · ${scoreInfo.gradeLevel}`,
    numericScore: scoreInfo.numericScore,
    gradeLevel: scoreInfo.gradeLevel,
    deductionReason: scoreInfo.deductionReason,
    suggestion: suggestions.join(' '),
    bottleneckType,
    bottleneckReason,
    topHotWindowSuggestion: '阶段性报告暂不包含热门窗口分析，建议运行至结束后查看完整报告。',
    summary: {
      avgWaitTime,
      avgSeatWaitTime,
      seatTurnoverRate,
      lossRate,
      maxCongestion,
      maxSeatWaiting,
      generated,
      finished,
      lost,
      queueLost: lost,
      served,
      bottleneckType
    },
    trend: trendHistory.value,
    arrivalTrend: fullArrivalSamples.value,
    windowPerformance
  };
}

function getCurrentLayout() {
  return backendLayout.value || createCanteenLayout(props.config);
}


function refreshWindowTooltip() {
  if (!windowTooltip.value || !hoveredWindowId.value) return;
  const target = (getCurrentLayout().windows || []).find(win => win.id === hoveredWindowId.value);
  const info = (snapshot.value?.windows || []).find(item => item.id === hoveredWindowId.value) || target;
  if (!target || !info) {
    windowTooltip.value = null;
    hoveredWindowId.value = null;
    return;
  }
  windowTooltip.value = {
    ...windowTooltip.value,
    id: target.id,
    dishName: info.dishName || target.dishName || target.id,
    qLen: info.qLen ?? 0,
    queueCount: info.queueCount ?? info.qLen ?? 0,
    approachingCount: info.approachingCount ?? 0,
    orderingCount: info.orderingCount ?? 0,
    capacityUsed: info.capacityUsed ?? info.qLen ?? 0,
    rank: info.popularityRank,
    avgWaitTime: info.avgWaitTime ?? 0
  };
}

function handleCanvasMove(event) {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const rect = canvas.getBoundingClientRect();
  const layout = getCurrentLayout();
  const x = ((event.clientX - rect.left) / rect.width) * layout.width;
  const y = ((event.clientY - rect.top) / rect.height) * layout.height;
  const target = (layout.windows || []).find(win => {
    const boxW = win.compact || layout.windows.length > 12 ? (win.boxW || 60) : (win.boxW || 108);
    const boxH = win.compact || layout.windows.length > 12 ? (win.boxH || 62) : (win.boxH || 76);
    return x >= win.x - boxW / 2 && x <= win.x + boxW / 2 && y >= win.y - boxH / 2 && y <= win.y + boxH / 2;
  });
  if (!target) {
    windowTooltip.value = null;
    hoveredWindowId.value = null;
    return;
  }
  hoveredWindowId.value = target.id;
  const info = (snapshot.value?.windows || []).find(item => item.id === target.id) || target;
  windowTooltip.value = {
    x: event.clientX - rect.left + 12,
    y: event.clientY - rect.top + 12,
    id: target.id,
    dishName: info.dishName || target.dishName || target.id,
    qLen: info.qLen ?? 0,
    queueCount: info.queueCount ?? info.qLen ?? 0,
    approachingCount: info.approachingCount ?? 0,
    orderingCount: info.orderingCount ?? 0,
    capacityUsed: info.capacityUsed ?? info.qLen ?? 0,
    rank: info.popularityRank,
    avgWaitTime: info.avgWaitTime ?? 0
  };
}

function clearWindowTooltip() {
  windowTooltip.value = null;
  hoveredWindowId.value = null;
}

function cleanup() {
  if (renderRaf) cancelAnimationFrame(renderRaf);
  renderRaf = 0;
  simulator?.stop();  simulator = null;
  player?.stop();     player = null;
}

onMounted(() => {
  if (props.useLocalFallback || !props.serverData) startLocal();
  else startBackend();
});
onBeforeUnmount(cleanup);
</script>

<template>
  <section class="page-card monitor-page">
    <!-- Left panel -->
    <aside class="side-panel left-panel">
      <div class="panel-section status-section">
        <span class="badge"><span class="status-dot"></span>{{ runningMode }}</span>
        <div v-if="config.sceneName" class="scene-name">{{ config.sceneName }}</div>
      </div>

      <div class="panel-section progress-section">
        <div class="progress-head">
          <span>仿真进度</span>
          <strong>{{ progress }}%</strong>
        </div>
        <div class="progress-track"><i :style="{ width: `${progress}%` }"></i></div>
        <div class="progress-time">
          <span><b class="tick-label">Tick</b> {{ snapshot?.tick ?? 0 }}</span>
          <span>/ {{ config.simDurationTick }}秒</span>
        </div>
      </div>

      <div class="panel-section metrics-section">
        <div class="section-label">实时指标</div>
        <div class="metrics-stack">
          <MetricCard label="在场" :value="activeCount" unit="人" />
          <MetricCard label="已占座位" :value="occupiedSeats" unit="个" tone="green" />
          <MetricCard label="等待座位" :value="waitingSeatCount" unit="人" tone="orange" />
          <MetricCard label="流失率" :value="lossRate" unit="%" tone="red" />
        </div>
      </div>

      <div class="panel-section avg-section">
        <div class="section-label">平均时长</div>
        <div class="avg-list">
          <div class="avg-row">
            <span>排队均值</span>
            <b>{{ avgWaitDisplay }}</b>
          </div>
          <div class="avg-row">
            <span>找座均值</span>
            <b>{{ avgSeatWaitDisplay }}</b>
          </div>
        </div>
      </div>
    </aside>

    <!-- Canvas area -->
    <section class="canvas-wrap">
      <div class="canvas-toolbar">
        <div class="toolbar-left">
          <strong class="bottleneck-label">{{ bottleneckType }}</strong>
          <span class="render-mode-tag">{{ renderMode }}模式</span>
          <button class="heatmap-toggle" :class="{ active: heatmapOn }" @click="heatmapOn = !heatmapOn">
            {{ heatmapOn ? '关闭热力图' : '座位热力图' }}
          </button>
        </div>
        <CanvasLegend />
      </div>
      <div class="canvas-stage">
        <canvas
          ref="canvasRef"
          class="sim-canvas"
          aria-label="食堂就餐仿真 Canvas 画布"
          @mousemove="handleCanvasMove"
          @mouseleave="clearWindowTooltip"
        ></canvas>
        <div v-if="windowTooltip" class="window-tooltip" :style="{ left: `${windowTooltip.x}px`, top: `${windowTooltip.y}px` }">
          <strong>{{ windowTooltip.id }} {{ windowTooltip.dishName }}</strong>
          <span>当前排队：{{ windowTooltip.queueCount }} 人</span>
          <span v-if="windowTooltip.approachingCount">正在前往：{{ windowTooltip.approachingCount }} 人</span>
          <span v-if="windowTooltip.orderingCount">正在取餐：{{ windowTooltip.orderingCount }} 人</span>
          <span>容量占用：{{ windowTooltip.capacityUsed }} / {{ config.maxQueueCapacity }}</span>
          <span v-if="windowTooltip.rank">热度排行：第 {{ windowTooltip.rank }} 名</span>
          <span>平均等待：{{ formatTickDuration(windowTooltip.avgWaitTime) }}</span>
        </div>
      </div>
    </section>

    <!-- Right panel -->
    <aside class="side-panel right-panel">
      <div class="panel-section control-section">
        <div class="control-status">
          <span class="conn-dot" :class="connectionStatus === '运行中' || connectionStatus === '回放中' ? 'conn-ok' : 'conn-idle'"></span>
          <span>{{ connectionStatus }}</span>
          <strong class="speed-display">{{ speedFactor }}×</strong>
        </div>

        <button class="primary-btn pause-btn" @click="togglePause">
          {{ paused ? '▶ 继续' : '⏸ 暂停' }}
        </button>

        <div class="speed-group">
          <button
            v-for="factor in speedOptions"
            :key="factor"
            class="speed-btn"
            :class="{ active: speedFactor === factor }"
            @click="changeSpeed(factor)"
          >{{ factor }}×</button>
        </div>

        <div class="action-row">
          <button class="ghost-btn action-btn" :disabled="reportRequesting" @click="generateCurrentReport">
            {{ reportRequesting ? '生成中...' : '📋 生成报告' }}
          </button>
          <button class="danger-btn action-btn" @click="emit('restart')">↩ 重配</button>
        </div>
      </div>

      <div class="panel-section arrival-section">
        <div class="section-label">
          <span>到达曲线</span>
          <small>近{{ ARRIVAL_SAMPLE_INTERVAL }}秒 <b>{{ latestArrival }}</b> 人 · 峰值 <b>{{ peakArrival }}</b></small>
        </div>
        <svg viewBox="0 0 220 64" preserveAspectRatio="none" class="arrival-chart">
          <defs>
            <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#0b63ce" stop-opacity="0.18"/>
              <stop offset="100%" stop-color="#0b63ce" stop-opacity="0"/>
            </linearGradient>
          </defs>
          <line x1="6" y1="58" x2="214" y2="58" stroke="rgba(100,116,139,0.2)" stroke-width="1"/>
          <polyline v-if="arrivalPolyline" :points="arrivalPolyline" fill="url(#areaGrad)" stroke="none"/>
          <polyline v-if="arrivalPolyline" :points="arrivalPolyline" fill="none" stroke="#0b63ce" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>

      <div class="panel-section windows-section">
        <div class="section-label">
          <span>热门窗口</span>
          <small>等待人数</small>
        </div>
        <div class="window-row" v-for="win in hotWindows" :key="win.id">
          <span class="rank-badge">{{ win.popularityRank }}</span>
          <div class="dish-info">
            <b>{{ win.dishName || win.id }}</b>
            <div class="queue-bar">
              <i :style="{ width: `${Math.min(100, (win.qLen / config.maxQueueCapacity) * 100)}%` }"></i>
            </div>
          </div>
          <strong class="queue-count">{{ win.qLen }}</strong>
        </div>
        <p v-if="!hotWindows.length" class="no-windows">暂无热门窗口数据</p>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.monitor-page {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr) 220px;
  align-items: start;
  gap: 10px;
  width: min(2200px, calc(100vw - 16px));
  padding: 8px;
}

/* ── Panels ── */
.side-panel {
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.panel-section {
  padding: 12px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.panel-section:last-child {
  border-bottom: none;
}

.section-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 11px;
  font-weight: 800;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.section-label small {
  font-weight: 700;
  letter-spacing: 0;
  text-transform: none;
  color: #94a3b8;
}

.section-label small b {
  color: var(--bjtu-blue-dark);
}

/* ── Status ── */
.status-section {
  padding: 14px;
}

.scene-name {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 900;
  color: var(--bjtu-blue-dark);
  letter-spacing: 0.02em;
}

/* ── Progress ── */
.progress-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 800;
  color: var(--bjtu-blue-dark);
}

.progress-track {
  height: 9px;
  border-radius: 999px;
  background: rgba(0, 64, 152, 0.1);
  overflow: hidden;
  margin-bottom: 6px;
}

.progress-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #004098, #38bdf8);
  transition: width 0.4s ease;
}

.progress-time {
  display: flex;
  justify-content: space-between;
  font-size: 10.5px;
  color: var(--muted);
  font-weight: 700;
}

.tick-label {
  display: inline-block;
  padding: 1px 6px;
  margin-right: 4px;
  border-radius: 6px;
  background: rgba(37, 99, 235, 0.10);
  color: #1d4ed8;
  font-weight: 800;
  font-size: 10px;
  letter-spacing: 0.3px;
}

/* ── Metrics ── */
.metrics-stack {
  display: grid;
  gap: 6px;
}

.metrics-stack :deep(.metric-card) {
  min-height: 56px;
  padding: 8px 10px;
  border-radius: 14px;
  box-shadow: none;
  border: 1px solid rgba(15, 23, 42, 0.07);
}

.metrics-stack :deep(.metric-card p) {
  font-size: 10.5px;
  margin-bottom: 3px;
}

.metrics-stack :deep(.metric-card strong) {
  font-size: 22px;
  line-height: 1;
}

/* ── Average times ── */
.avg-list { display: grid; gap: 6px; }

.avg-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.03);
  font-size: 11px;
  color: #475569;
  font-weight: 700;
}

.avg-row b {
  color: var(--bjtu-blue-dark);
  font-size: 12px;
}

/* ── Canvas ── */
.canvas-wrap {
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
  padding: 10px;
  overflow: hidden;
}

.canvas-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 4px 4px 8px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bottleneck-label {
  color: var(--bjtu-blue-dark);
  font-size: 13px;
  font-weight: 900;
}

.render-mode-tag {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
}

.heatmap-toggle {
  font-size: 11px;
  font-weight: 800;
  color: #64748b;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  border: 1.5px solid rgba(15, 23, 42, 0.08);
  cursor: pointer;
  transition: all 0.18s ease;
}

.heatmap-toggle:hover {
  background: rgba(220, 38, 38, 0.08);
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.2);
}

.heatmap-toggle.active {
  color: #fff;
  background: linear-gradient(135deg, #ef4444, #f97316);
  border-color: transparent;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.25);
}

.canvas-stage {
  position: relative;
}

.window-tooltip {
  position: absolute;
  z-index: 5;
  display: grid;
  gap: 3px;
  min-width: 150px;
  padding: 9px 11px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.88);
  color: #fff;
  font-size: 11px;
  line-height: 1.45;
  pointer-events: none;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.20);
}

.window-tooltip strong {
  font-size: 12px;
}

.sim-canvas {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 1600 / 900;
  border-radius: 16px;
  border: 1px solid rgba(0, 64, 152, 0.12);
  background: #f8fbff;
}


/* ── Control section (right panel) ── */
.control-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #475569;
  font-weight: 700;
}

.conn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #94a3b8;
}

.conn-dot.conn-ok {
  background: #16a34a;
  box-shadow: 0 0 6px rgba(22, 163, 74, 0.4);
}

.speed-display {
  margin-left: auto;
  color: var(--bjtu-blue-dark);
  font-size: 18px;
}

.pause-btn {
  width: 100%;
  min-height: 40px;
  font-size: 14px;
  border-radius: 14px;
  margin-bottom: 10px;
}

.speed-group {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-bottom: 10px;
}

.speed-btn {
  min-height: 32px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 800;
  color: #334155;
  background: #fff;
  border: 1.5px solid rgba(15, 23, 42, 0.09);
  transition: all 0.16s ease;
}

.speed-btn.active {
  color: #fff;
  background: linear-gradient(135deg, #004098, #0b63ce);
  border-color: transparent;
  box-shadow: 0 6px 16px rgba(0, 64, 152, 0.2);
}

.action-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.action-btn {
  min-height: 36px;
  padding: 0 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 800;
}

/* ── Arrival chart ── */
.arrival-chart {
  display: block;
  width: 100%;
  height: 64px;
}

/* ── Window list ── */
.window-row {
  display: grid;
  grid-template-columns: 24px 1fr 32px;
  align-items: center;
  gap: 8px;
  margin-bottom: 9px;
  font-size: 11.5px;
  font-weight: 800;
  color: #334155;
}

.rank-badge {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #ef4444, #f97316);
  font-size: 10px;
  flex-shrink: 0;
}

.dish-info b {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  margin-bottom: 4px;
  font-size: 11px;
}

.queue-bar {
  height: 5px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.queue-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #38bdf8, #f97316, #dc2626);
}

.queue-count {
  text-align: right;
  color: var(--bjtu-blue-dark);
  font-size: 14px;
}

.no-windows {
  font-size: 12px;
  color: var(--muted);
  text-align: center;
  padding: 8px 0;
}

/* ── Responsive ── */
@media (max-width: 1800px) {
  .monitor-page {
    grid-template-columns: 186px minmax(0, 1fr) 204px;
  }
}

@media (max-width: 1200px) {
  .monitor-page {
    grid-template-columns: 1fr;
    width: calc(100vw - 24px);
  }
}
</style>
