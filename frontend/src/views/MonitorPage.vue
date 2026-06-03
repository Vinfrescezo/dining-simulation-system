<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import CanvasLegend from '../components/CanvasLegend.vue';
import MetricCard from '../components/MetricCard.vue';
import { LocalDiningSimulator } from '../services/localSimulator';
import { EventPlayer } from '../services/eventPlayer';
import { renderCanteen } from '../composables/useCanvasRenderer';

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
const windowSamples = ref(new Map());
const speedOptions = [0.5, 1, 2, 4, 8];

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
  nextTick(() => scheduleRender(normalized));

  if (data?.type === 'FINISHED' || data?.finished === true || data?.done === true) {
    finish(data.report || buildLocalReport());
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
    arrivalSamples.value.push({ tick: currentTick, arrivals: pendingArrivalCount });
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
  if (props.useLocalFallback || !serverReport.value) {
    finish(buildLocalReport('阶段性报告'));
    return;
  }
  finish(serverReport.value);
}

function finish(report) {
  cleanup();
  emit('finished', report ?? buildLocalReport());
}

function buildLocalReport(reportType = '最终报告') {
  const generated = snapshot.value?.generated ?? 0;
  const lost      = snapshot.value?.lost ?? 0;
  const maxCongestion = trendHistory.value.reduce((max, item) => Math.max(max, item.activeCount), 0);
  const lossRateValue = generated ? lost / generated : 0;
  const avgQueueMap = Array.from(windowSamples.value.entries()).map(([id, samples]) => ({
    id,
    dishName:       snapshot.value?.windows?.find(w => w.id === id)?.dishName ?? id,
    popularityRank: snapshot.value?.windows?.find(w => w.id === id)?.popularityRank ?? 99,
    avgQueueLength: samples.length ? samples.reduce((a, b) => a + b, 0) / samples.length : 0,
    avgWaitTime: 0,
    totalServedCount: snapshot.value?.windows?.find(w => w.id === id)?.served ?? 0
  }));
  return {
    simId: `SIM_${Date.now()}`,
    createdAt: new Date().toLocaleString(),
    source: 'local',
    reportType,
    score: lossRateValue > 0.12 ? '极度拥挤' : lossRateValue > 0.05 ? '偏拥挤' : '基本可控',
    suggestion: lossRateValue > 0.08 ? '建议增加 1—2 个窗口，观察等座区瞬时峰值。' : '当前配置基本可行，建议保留监控数据进行多轮对比。',
    summary: {
      avgWaitTime:     snapshot.value?.stats?.avgWaitTime ?? 0,
      avgSeatWaitTime: snapshot.value?.stats?.avgSeatWaitTime ?? 0,
      seatTurnoverRate: props.config.seatCount ? (generated - lost) / props.config.seatCount : 0,
      lossRate: lossRateValue, maxCongestion,
      generated, lost, served: generated - lost,
      finished: snapshot.value?.finished ?? 0
    },
    trend: trendHistory.value,
    arrivalTrend: arrivalSamples.value,
    windowPerformance: avgQueueMap
  };
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
          <span>{{ snapshot?.tick ?? 0 }}秒</span>
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
            <span>等座均值</span>
            <b>{{ avgSeatWaitDisplay }}</b>
          </div>
          <div class="avg-row">
            <span>等座放弃</span>
            <b>{{ seatAbandoned }} 人</b>
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
      <canvas ref="canvasRef" class="sim-canvas" aria-label="食堂就餐仿真 Canvas 画布"></canvas>
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
