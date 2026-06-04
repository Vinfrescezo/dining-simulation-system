<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';
import MetricCard from '../components/MetricCard.vue';
import { downloadCsv } from '../utils/download';
import { fetchSimulationHistory } from '../services/api';

const props = defineProps({
  report: { type: Object, required: true },
  config: { type: Object, required: true }
});

const emit = defineEmits(['restart', 'back']);
const trendRef = ref(null);
const barRef = ref(null);
const historyList = ref([]);
const historyLoading = ref(false);
let trendChart = null;
let barChart = null;

const summary = computed(() => props.report?.summary ?? {});
const avgWaitMinute = computed(() => ((summary.value.avgWaitTime ?? 0) / 60).toFixed(1));
const avgSeatWaitMinute = computed(() => ((summary.value.avgSeatWaitTime ?? 0) / 60).toFixed(1));
const lossRatePercent = computed(() => (((summary.value.lossRate ?? 0) * 100)).toFixed(1));
const turnover = computed(() => (summary.value.seatTurnoverRate ?? 0).toFixed(2));
const reportSource = computed(() => props.report?.source === 'backend' ? '后端真实统计' : props.report?.source === 'local' ? '本地演示统计' : '后端报告缺失');
const reportType = computed(() => props.report?.reportType || '最终报告');
const reportNote = computed(() => props.report?.reportNote || '本报告展示当前可用的仿真统计数据。');
const bottleneckType = computed(() => props.report?.bottleneckType || summary.value.bottleneckType || '未诊断');
const bottleneckReason = computed(() => props.report?.bottleneckReason || '本次报告未返回瓶颈原因。');
const generated = computed(() => summary.value.generated ?? 0);
const served = computed(() => summary.value.served ?? 0);
const finished = computed(() => summary.value.finished ?? 0);
const queueLost = computed(() => summary.value.queueLost ?? summary.value.lost ?? 0);
const serviceRate = computed(() => {
  const g = generated.value;
  return g > 0 ? ((served.value / g) * 100).toFixed(1) : '0.0';
});

function tickToMinutes(value) {
  return (Number(value || 0) / 60).toFixed(2);
}

function formatTickDuration(value) {
  const totalSeconds = Math.round(Number(value || 0));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes <= 0) return `${seconds}秒`;
  return `${minutes}分${String(seconds).padStart(2, '0')}秒`;
}

function getField(row, camel, snakeUpper) {
  return row?.[camel] ?? row?.[snakeUpper] ?? row?.[snakeUpper?.toLowerCase?.()] ?? '-';
}

async function loadHistory() {
  historyLoading.value = true;
  try {
    historyList.value = await fetchSimulationHistory(8);
  } catch {
    historyList.value = [];
  } finally {
    historyLoading.value = false;
  }
}

function renderCharts() {
  if (!props.report) return;
  trendChart?.dispose();
  barChart?.dispose();
  trendChart = echarts.init(trendRef.value);
  barChart = echarts.init(barRef.value);

  const trend = props.report.trend || [];
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 6, right: 12, textStyle: { fontWeight: 700 } },
    grid: { left: 52, right: 28, top: 48, bottom: 42 },
    xAxis: { type: 'category', data: trend.map(item => item.tick), name: '时间（秒）', nameLocation: 'end', nameTextStyle: { color: '#64748b' } },
    yAxis: { type: 'value', name: '人数', nameTextStyle: { color: '#64748b' } },
    series: [
      {
        name: '食堂拥堵人数',
        type: 'line', smooth: true,
        areaStyle: { opacity: 0.15 },
        lineStyle: { width: 2.5 },
        data: trend.map(item => item.activeCount)
      },
      {
        name: '端盘等座人数',
        type: 'line', smooth: true,
        lineStyle: { width: 2 },
        data: trend.map(item => item.waitingSeatCount ?? 0)
      }
    ]
  });

  const windows = props.report.windowPerformance || [];
  barChart.setOption({
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter(params) {
        const idx = params?.[0]?.dataIndex ?? 0;
        const item = windows[idx] || {};
        const lines = [
          `<strong>${item.id || ''} ${item.dishName || ''}</strong>`,
          item.popularityRank ? `热度排行：第 ${item.popularityRank} 名` : '',
          ...params.map(p => `${p.marker}${p.seriesName}：${p.value}${p.seriesName.includes('时长') ? ' 分钟' : ''}`)
        ].filter(Boolean);
        return lines.join('<br/>');
      }
    },
    legend: { top: 6, right: 12, textStyle: { fontWeight: 700 } },
    grid: { left: 58, right: 30, top: 54, bottom: 38, containLabel: true },
    xAxis: {
      type: 'category', data: windows.map(item => item.id || '窗口'),
      axisTick: { alignWithLabel: true }, axisLabel: { interval: 0, fontSize: 12 }
    },
    yAxis: { type: 'value', name: '队长/分钟', nameTextStyle: { color: '#64748b' } },
    series: [
      {
        name: '平均队长', type: 'bar', barMaxWidth: 32,
        data: windows.map(item => Number(item.avgQueueLength?.toFixed?.(2) ?? item.avgQueueLength ?? 0))
      },
      {
        name: '平均排队时长/分钟', type: 'bar', barMaxWidth: 32,
        data: windows.map(item => Number(tickToMinutes(item.avgWaitTime)))
      }
    ]
  });
}

function resizeCharts() {
  trendChart?.resize();
  barChart?.resize();
}

function exportCsv() {
  const rows = [
    ['指标', '值'],
    ['仿真编号', props.report.simId],
    ['生成时间', props.report.createdAt],
    ['预计人数', props.config.studentCount],
    ['开放窗口数', props.config.windowCount],
    ['座位数', props.config.seatCount],
    ['仿真时长/秒', props.config.simDurationTick],
    ['入场总人数', generated.value],
    ['完成就餐人数', finished.value],
    ['成功打饭人数', served.value],
    ['窗口流失人数', queueLost.value],
    ['平均排队时长/min', avgWaitMinute.value],
    ['平均找座时长/min', avgSeatWaitMinute.value],
    ['座位周转率', turnover.value],
    ['窗口流失率/%', lossRatePercent.value],
    ['服务完成率/%', serviceRate.value],
    ['同屏峰值人数', summary.value.maxCongestion],
    ['找座峰值人数', summary.value.maxSeatWaiting],
    ['主要瓶颈', bottleneckType.value],
    ['瓶颈原因', bottleneckReason.value],
    ['综合评分', props.report.score],
    ['优化建议', props.report.suggestion]
  ];
  downloadCsv(`simulation-report-${props.report.simId || Date.now()}.csv`, rows);
}

onMounted(() => {
  nextTick(renderCharts);
  loadHistory();
  window.addEventListener('resize', resizeCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts);
  trendChart?.dispose();
  barChart?.dispose();
});

watch(() => props.report, () => nextTick(renderCharts), { deep: true });
</script>

<template>
  <section class="page-card report-page">
    <div class="report-header">
      <div class="header-left">
        <span class="badge"><span class="status-dot"></span> {{ reportType }}</span>
        <h2>本次仿真状况：<em>{{ report.score }}</em></h2>
        <p class="meta-line">{{ report.createdAt }} · {{ reportSource }}</p>
        <p class="report-note">
          {{ reportNote }}
          <span>
            到达模型：泊松分布（每 Tick 独立抽样）；打饭时长：约 {{ config.orderingTime ?? 28 }} 秒；用餐时长：约 {{ ((config.eatingTime ?? 480) / 60).toFixed(0) }} 分钟；流失仅记录因窗口队列已满被拒入场的学生。1 Tick ≈ 1 秒，页面已换算为分钟/秒展示。
          </span>
        </p>
      </div>
      <div class="report-actions">
        <button class="ghost-btn" @click="emit('back')">← 返回监控</button>
        <button class="secondary-btn" @click="exportCsv">↓ 导出 CSV</button>
        <button class="primary-btn" @click="emit('restart')">↺ 重新仿真</button>
      </div>
    </div>

    <div class="metric-strip">
      <MetricCard label="平均排队时长" :value="avgWaitMinute" unit="min" hint="从进入队伍到开始打饭" />
      <MetricCard label="平均找座时长" :value="avgSeatWaitMinute" unit="min" hint="从打完饭到坐下用餐" tone="orange" />
      <MetricCard label="座位周转率" :value="turnover" unit="次/座" hint="完成就餐人数 / 总座位数" tone="green" />
      <MetricCard label="窗口流失率" :value="lossRatePercent" unit="%" hint="窗口队列已满，学生被拒于门外" tone="red" />
      <MetricCard label="同屏峰值" :value="summary.maxCongestion ?? 0" unit="人" hint="单帧最高在场人数" tone="purple" />
      <MetricCard label="服务完成率" :value="serviceRate" unit="%" hint="成功打到饭 / 入场总人数" tone="green" />
    </div>

    <div class="diagnosis-row">
      <article class="diagnosis-card">
        <span class="diag-label">主要瓶颈</span>
        <strong>{{ bottleneckType }}</strong>
        <p>{{ bottleneckReason }}</p>
      </article>
      <article class="diagnosis-card hot-card">
        <span class="diag-label">热门窗口建议</span>
        <strong>菜品分流</strong>
        <p>{{ report.topHotWindowSuggestion || '热门窗口负载暂未明显失衡。' }}</p>
      </article>
      <article class="suggestion-card">
        <span class="diag-label">综合建议</span>
        <p>{{ report.suggestion }}</p>
      </article>
    </div>

    <div class="chart-grid">
      <article class="chart-card">
        <h3>拥堵趋势</h3>
        <div ref="trendRef" class="chart"></div>
      </article>
      <article class="chart-card">
        <h3>窗口排队对比（已换算为分钟）</h3>
        <div ref="barRef" class="chart"></div>
      </article>
    </div>

    <article class="table-card">
      <h3>窗口性能明细</h3>
      <p class="table-note">队长不包含正在打饭的学生；平均排队时长包含已完成排队及当前等待学生的等待时间。</p>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>窗口</th>
              <th>菜品 / 热度</th>
              <th>平均等待队长</th>
              <th>平均排队时长</th>
              <th>峰值队长</th>
              <th>服务人数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in report.windowPerformance" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.dishName || '-' }} / {{ item.popularityRank ? `热度${item.popularityRank}` : '-' }}</td>
              <td>{{ Number(item.avgQueueLength ?? 0).toFixed(2) }}</td>
              <td>{{ formatTickDuration(item.avgWaitTime) }}（{{ tickToMinutes(item.avgWaitTime) }}分钟）</td>
              <td>{{ item.peakQueueLength ?? 0 }}</td>
              <td>{{ item.totalServedCount ?? 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <article class="table-card history-card">
      <div class="history-title">
        <h3>历史仿真记录</h3>
        <span class="history-count">{{ historyLoading ? '读取中...' : `最近 ${historyList.length} 次` }}</span>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>时间</th><th>评分</th><th>瓶颈</th>
              <th>排队时长</th><th>等座时长</th><th>流失率</th><th>服务人数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in historyList" :key="getField(row, 'sim_id', 'SIM_ID')">
              <td>{{ getField(row, 'created_at', 'CREATED_AT') }}</td>
              <td>{{ getField(row, 'score', 'SCORE') }}</td>
              <td>{{ getField(row, 'bottleneck_type', 'BOTTLENECK_TYPE') }}</td>
              <td>{{ formatTickDuration(getField(row, 'avg_wait_time', 'AVG_WAIT_TIME') || 0) }}</td>
              <td>{{ formatTickDuration(getField(row, 'avg_seat_wait_time', 'AVG_SEAT_WAIT_TIME') || 0) }}</td>
              <td>{{ (Number(getField(row, 'loss_rate', 'LOSS_RATE') || 0) * 100).toFixed(1) }}%</td>
              <td>{{ getField(row, 'served', 'SERVED') }}</td>
            </tr>
            <tr v-if="!historyLoading && !historyList.length">
              <td colspan="7" class="empty-row">暂无历史记录。完成一次后端仿真后，H2 数据库会自动保存报告。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>

<style scoped>
.report-page {
  padding: 36px 40px;
}

/* ── Header ── */
.report-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 28px;
  margin-bottom: 28px;
  padding-bottom: 28px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.header-left .badge { margin-bottom: 14px; }

.report-header h2 {
  margin: 0 0 8px;
  color: var(--bjtu-blue-dark);
  font-size: 36px;
  font-weight: 900;
}

.report-header h2 em {
  font-style: normal;
  background: linear-gradient(135deg, #004098, #0b63ce);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.meta-line {
  margin: 0 0 6px;
  color: var(--muted);
  font-size: 14px;
}

.report-note {
  margin: 0;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.6;
}

.report-note span {
  color: var(--muted);
  font-weight: 600;
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

/* ── Metrics ── */
.metric-strip {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

/* ── Diagnosis row ── */
.diagnosis-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1.4fr;
  gap: 16px;
  margin-bottom: 24px;
}

.diagnosis-card,
.suggestion-card {
  padding: 22px 24px;
  border-radius: 24px;
}

.diagnosis-card {
  background: linear-gradient(135deg, #eef6ff, #ffffff);
  border: 1.5px solid rgba(0, 64, 152, 0.14);
}

.hot-card {
  background: linear-gradient(135deg, #fff7ed, #ffffff);
  border-color: rgba(249, 115, 22, 0.18);
}

.suggestion-card {
  background: linear-gradient(135deg, #fff1f2, #fff7ed);
  border: 1.5px solid rgba(220, 38, 38, 0.16);
}

.diag-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 800;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.diagnosis-card strong {
  display: block;
  margin-bottom: 10px;
  color: var(--bjtu-blue-dark);
  font-size: 22px;
  font-weight: 900;
}

.suggestion-card strong {
  display: block;
  margin-bottom: 8px;
  font-size: 18px;
  font-weight: 900;
  color: #7f1d1d;
}

.diagnosis-card p,
.suggestion-card p {
  margin: 0;
  line-height: 1.7;
  font-weight: 700;
  color: #334155;
  font-size: 14px;
}

/* ── Charts ── */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.chart-card {
  padding: 22px 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.06);
}

.chart-card h3 {
  margin: 0 0 16px;
  color: var(--bjtu-blue-dark);
  font-size: 16px;
  font-weight: 900;
}

.chart {
  width: 100%;
  height: 380px;
}

/* ── Tables ── */
.table-card {
  margin-bottom: 20px;
  padding: 24px 26px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.06);
}

.table-card h3 {
  margin: 0 0 6px;
  color: var(--bjtu-blue-dark);
  font-size: 18px;
  font-weight: 900;
}

.table-note {
  margin: 0 0 16px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.table-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th, td {
  padding: 13px 16px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
  text-align: left;
  white-space: nowrap;
}

th {
  color: var(--bjtu-blue-dark);
  background: rgba(0, 64, 152, 0.06);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

th:first-child { border-radius: 12px 0 0 0; }
th:last-child { border-radius: 0 12px 0 0; }

td {
  color: #334155;
  font-weight: 700;
  font-size: 14px;
}

tr:last-child td { border-bottom: none; }

.empty-row {
  text-align: center;
  color: var(--muted);
  padding: 20px;
}

.history-card {}

.history-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.history-count {
  font-size: 13px;
  color: var(--muted);
  font-weight: 700;
}

@media (max-width: 1280px) {
  .metric-strip { grid-template-columns: repeat(3, 1fr); }
  .diagnosis-row { grid-template-columns: 1fr 1fr; }
  .diagnosis-row .suggestion-card { grid-column: span 2; }
  .chart-grid { grid-template-columns: 1fr; }
}
</style>
