<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';
import MetricCard from '../components/MetricCard.vue';
import { downloadCsv } from '../utils/download';
import { fetchSimulationHistory, generateAiReport } from '../services/api';

const props = defineProps({
  report: { type: Object, required: true },
  config: { type: Object, required: true }
});

const emit = defineEmits(['restart', 'back']);
const trendRef = ref(null);
const barRef = ref(null);
const arrivalRef = ref(null);
const historyList = ref([]);
const historyLoading = ref(false);
const aiLoading = ref(false);
const aiResult = ref('');
const aiError = ref('');
let trendChart = null;
let barChart = null;
let arrivalChart = null;

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

const numericScore = computed(() => {
  if (typeof props.report?.numericScore === 'number') return props.report.numericScore;
  const match = String(props.report?.score || '').match(/(\d{1,3})/);
  if (match) return Math.max(0, Math.min(100, Number(match[1])));
  const fallback = { '优秀': 92, '良好': 84, '基本可控': 74, '偏拥挤': 65, '严重拥挤': 55, '运行顺畅': 88, '极度拥挤': 50 };
  return fallback[props.report?.score] ?? 0;
});
const gradeLevel = computed(() => props.report?.gradeLevel || String(props.report?.score || '').replace(/^\d+分\s*·\s*/, '') || '未评级');
const deductionReason = computed(() => props.report?.deductionReason || '暂无明显扣分项。');
const reportInfoItems = computed(() => [
  ['生成时间', props.report?.createdAt || '-'],
  ['报告来源', reportSource.value],
  ['生成节点', reportType.value],
  ['到达模型', props.config.mealPeriod === 'DINNER' ? '晚间高峰' : '中午高峰'],
  ['窗口服务模型', '按菜品复杂度独立计算（18-50 秒/人）'],
  ['用餐时长', `约 ${Math.round((props.config.eatingTime ?? 480) / 60)} 分钟`],
  ['流失口径', '仅统计窗口队列满载导致的放弃就餐'],
  ['时间换算', '1 Tick ≈ 1 秒']
]);

function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function highlightText(text) {
  const escaped = escapeHtml(text);
  return escaped
    .replace(/(严重拥挤|偏拥挤|窗口服务瓶颈|座位资源瓶颈|双重瓶颈|流失率|平均排队|找座等待|热门窗口|增加\s*\d+\s*个窗口)/g, '<mark class="ai-keyword danger">$1</mark>')
    .replace(/(优秀|良好|基本可控|运行基本均衡|运行顺畅|可控|建议|优化|分流|错峰)/g, '<mark class="ai-keyword good">$1</mark>')
    .replace(/(\d+(?:\.\d+)?\s*(?:%|人|分钟|秒|分|次\/座))/g, '<mark class="ai-keyword number">$1</mark>');
}

const aiSections = computed(() => {
  const raw = aiError.value || aiResult.value || '';
  const cleaned = raw
    .replace(/```[\s\S]*?```/g, m => m.replace(/```/g, ''))
    .split('\n')
    .flatMap(line => {
      const normalized = line.replace(/^\s{0,3}#{1,6}\s*/, '').replace(/^[-*]\s*/, '').replace(/^\d+[.、]\s*/, '').trim();
      if (!normalized) return [];
      if (normalized.length > 60 && /[；;]/.test(normalized)) {
        return normalized.split(/[；;]/).map(item => item.trim()).filter(Boolean);
      }
      return [normalized];
    })
    .filter(Boolean);
  const sections = [];
  let current = { title: '智能分析', lines: [] };
  const titlePattern = /^(总体判断|主要问题|问题分析|窗口排队|座位压力|人群流失|优化建议|管理建议|结论)[:：]?(.*)$/;
  for (const line of cleaned) {
    const match = line.match(titlePattern);
    if (match) {
      if (current.lines.length) sections.push(current);
      current = { title: match[1], lines: [] };
      const rest = match[2]?.trim();
      if (rest) current.lines.push(rest);
    } else {
      current.lines.push(line);
    }
  }
  if (current.lines.length) sections.push(current);
  return sections.length ? sections : [{ title: '智能分析', lines: cleaned.length ? cleaned : ['暂无有效分析内容。'] }];
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
  arrivalChart?.dispose();
  trendChart = echarts.init(trendRef.value);
  barChart = echarts.init(barRef.value);
  if (arrivalRef.value) arrivalChart = echarts.init(arrivalRef.value);

  const trend = props.report.trend || [];
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 6, right: 12, textStyle: { fontWeight: 700 } },
    grid: { left: 52, right: 28, top: 48, bottom: 42 },
    xAxis: { type: 'category', data: trend.map(item => item.tick), name: '时间（秒）', nameLocation: 'end', nameTextStyle: { color: '#64748b' } },
    yAxis: { type: 'value', name: '人数', nameTextStyle: { color: '#64748b' } },
    series: [
      {
        name: '在场人数',
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
          item.baseServiceTimeSeconds ? `基础服务时长：${item.baseServiceTimeSeconds}秒/人` : '',
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

  // 人数到达曲线
  if (arrivalChart) {
    const arrivals = props.report.arrivalTrend || [];
    arrivalChart.setOption({
      tooltip: {
        trigger: 'axis',
        formatter(params) {
          const p = params?.[0];
          if (!p) return '';
          return `Tick ${p.axisValue}<br/>${p.marker}本采样到达：${p.value} 人`;
        }
      },
      legend: { top: 6, right: 12, textStyle: { fontWeight: 700 } },
      grid: { left: 52, right: 28, top: 48, bottom: 42 },
      xAxis: {
        type: 'category',
        data: arrivals.map(item => item.tick),
        name: '时间（秒）',
        nameLocation: 'end',
        nameTextStyle: { color: '#64748b' }
      },
      yAxis: { type: 'value', name: '到达人数', minInterval: 1, nameTextStyle: { color: '#64748b' } },
      dataZoom: arrivals.length > 120 ? [
        { type: 'inside', start: 0, end: 100 },
        { type: 'slider', height: 18, bottom: 8, start: 0, end: 100 }
      ] : [],
      series: [
        {
          name: '每 10 秒到达',
          type: 'bar',
          barMaxWidth: 14,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#3b82f6' },
              { offset: 1, color: '#93c5fd' }
            ]),
            borderRadius: [3, 3, 0, 0]
          },
          data: arrivals.map(item => item.arrivals ?? 0)
        }
      ]
    });
  }
}

function resizeCharts() {
  trendChart?.resize();
  barChart?.resize();
  arrivalChart?.resize();
}

async function requestAiReport() {
  aiLoading.value = true;
  aiError.value = '';
  try {
    const result = await generateAiReport(props.report);
    aiResult.value = result?.content || '智能分析未返回有效内容。';
    if (result?.available === false) aiError.value = aiResult.value;
  } catch (err) {
    aiResult.value = '';
    aiError.value = '智能分析暂不可用，请检查后端服务、DeepSeek API Key 或网络连接。';
  } finally {
    aiLoading.value = false;
  }
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
    ['就餐时段', props.config.mealPeriod === 'DINNER' ? '晚间高峰' : '中午高峰'],
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
    ['综合得分', numericScore.value],
    ['五级评级', gradeLevel.value],
    ['主要扣分项', deductionReason.value],
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
  arrivalChart?.dispose();
});

watch(() => props.report, () => nextTick(renderCharts), { deep: true });
</script>

<template>
  <section class="page-card report-page">
    <div class="report-header">
      <div class="header-left">
        <span class="badge"><span class="status-dot"></span> {{ reportType }}</span>
        <h2>本次评级：<em>{{ gradeLevel }}</em></h2>
        <div class="score-summary">
          <div class="score-badge">
            <strong>{{ numericScore }}</strong><span>分</span>
          </div>
          <p>主要扣分项：{{ deductionReason }}</p>
        </div>
        <div class="report-info-grid">
          <div v-for="item in reportInfoItems" :key="item[0]" class="report-info-item">
            <span>{{ item[0] }}</span>
            <strong>{{ item[1] }}</strong>
          </div>
        </div>
        <p class="report-note">{{ reportNote }}</p>
      </div>
      <div class="header-right">
        <div class="report-actions">
          <button class="ghost-btn" @click="emit('back')">← 返回监控</button>
          <button class="secondary-btn" @click="exportCsv">↓ 导出数据</button>
          <button class="secondary-btn" :disabled="aiLoading" @click="requestAiReport">{{ aiLoading ? '生成中...' : (aiResult ? '重新生成智能分析' : '生成智能分析') }}</button>
          <button class="primary-btn" @click="emit('restart')">↺ 重新仿真</button>
        </div>
        <div class="header-diagnosis-grid">
          <article class="diagnosis-card">
            <span class="diag-label">主要瓶颈</span>
            <strong>{{ bottleneckType }}</strong>
            <p>{{ bottleneckReason }}</p>
          </article>
          <article class="suggestion-card">
            <span class="diag-label">综合建议</span>
            <p>{{ report.suggestion }}</p>
          </article>
        </div>
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

    <article v-if="aiResult || aiError || aiLoading" class="ai-card">
      <div class="ai-card-head">
        <span class="diag-label">DeepSeek 智能分析</span>
        <strong>{{ aiLoading ? '正在生成分析报告...' : '智能分析报告' }}</strong>
      </div>
      <p v-if="aiLoading" class="ai-loading-text">正在根据本次仿真指标生成分析，请稍候。</p>
      <div v-else class="ai-section-grid">
        <section v-for="section in aiSections" :key="section.title" class="ai-section">
          <h4>{{ section.title }}</h4>
          <p
            v-for="(line, index) in section.lines"
            :key="index"
            class="ai-line"
            :class="{ risk: /严重|偏高|不足|瓶颈|流失/.test(line), recommend: /建议|优化|分流|错峰|增加/.test(line) }"
            v-html="highlightText(line)"
          ></p>
        </section>
      </div>
    </article>

    <div class="chart-grid">
      <article class="chart-card">
        <h3>在场人数变化</h3>
        <div ref="trendRef" class="chart"></div>
      </article>
      <article class="chart-card">
        <h3>窗口排队对比（已换算为分钟）</h3>
        <div ref="barRef" class="chart"></div>
      </article>
    </div>

    <article class="chart-card full-chart-card">
      <h3>人数到达曲线</h3>
      <div ref="arrivalRef" class="chart"></div>
    </article>

    <article class="table-card">
      <h3>窗口性能明细</h3>
      <p class="table-note">队长不包含正在取餐的学生；平均排队时长包含已完成排队及当前等待学生的等待时间。</p>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>窗口</th>
              <th>菜品 / 热度</th>
              <th>基础服务</th>
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
              <td>{{ item.baseServiceTimeSeconds ? `${item.baseServiceTimeSeconds}秒` : '-' }}</td>
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
              <th>排队时长</th><th>找座时长</th><th>流失率</th><th>服务人数</th>
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
  display: grid;
  grid-template-columns: minmax(520px, 0.92fr) minmax(520px, 1.08fr);
  align-items: stretch;
  gap: 28px;
  margin-bottom: 28px;
  padding-bottom: 28px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.header-left,
.header-right {
  min-width: 0;
}

.header-right {
  display: flex;
  flex-direction: column;
  gap: 14px;
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

.score-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 10px 0 16px;
}

.score-badge {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 10px 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, #004098, #0b63ce);
  color: #fff;
  box-shadow: 0 10px 24px rgba(0, 64, 152, 0.18);
}

.score-badge strong { font-size: 28px; line-height: 1; }
.score-badge span { font-size: 14px; font-weight: 900; }

.score-summary p {
  margin: 0;
  color: #334155;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.6;
}

.report-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0 10px;
}

.report-info-item {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(0, 64, 152, 0.045);
  border: 1px solid rgba(0, 64, 152, 0.08);
}

.report-info-item span {
  display: block;
  margin-bottom: 4px;
  color: #64748b;
  font-size: 11px;
  font-weight: 900;
}

.report-info-item strong {
  display: block;
  color: #1e293b;
  font-size: 13px;
  font-weight: 900;
  line-height: 1.35;
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

.header-diagnosis-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.header-diagnosis-grid .diagnosis-card,
.header-diagnosis-grid .suggestion-card {
  min-height: 0;
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

.ai-card {
  margin-bottom: 24px;
  padding: 24px 26px;
  border-radius: 24px;
  background: linear-gradient(135deg, #eef6ff, #ffffff);
  border: 1.5px solid rgba(0, 64, 152, 0.14);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.06);
}

.ai-card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 10px;
}

.ai-card-head strong {
  color: var(--bjtu-blue-dark);
  font-size: 20px;
  font-weight: 900;
}

.ai-loading-text {
  margin: 0;
  line-height: 1.75;
  color: #334155;
  font-weight: 800;
  font-size: 14px;
}

.ai-section-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.ai-section {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(0, 64, 152, 0.1);
}

.ai-section h4 {
  margin: 0 0 10px;
  color: var(--bjtu-blue-dark);
  font-size: 17px;
  font-weight: 950;
}

.ai-line {
  margin: 10px 0 0;
  padding-left: 12px;
  border-left: 3px solid rgba(0, 64, 152, 0.14);
  line-height: 1.82;
  color: #334155;
  font-size: 14.5px;
  font-weight: 750;
}

.ai-line.risk { color: #7f1d1d; }
.ai-line.recommend { color: #075985; }

:deep(.ai-keyword) {
  padding: 1px 4px;
  border-radius: 6px;
  font-weight: 950;
  background: rgba(0, 64, 152, 0.08);
}
:deep(.ai-keyword.danger) { color: #b91c1c; background: rgba(254, 226, 226, 0.95); }
:deep(.ai-keyword.good) { color: #047857; background: rgba(220, 252, 231, 0.9); }
:deep(.ai-keyword.number) { color: #0b63ce; background: rgba(219, 234, 254, 0.95); }

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

.full-chart-card {
  margin-bottom: 24px;
}

.full-chart-card .chart {
  height: 260px;
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
  .report-header { grid-template-columns: 1fr; }
  .report-actions { justify-content: flex-start; }
  .report-info-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ai-section-grid { grid-template-columns: 1fr; }
  .metric-strip { grid-template-columns: repeat(3, 1fr); }
  .diagnosis-row { grid-template-columns: 1fr 1fr; }
  .diagnosis-row .suggestion-card { grid-column: span 2; }
  .chart-grid { grid-template-columns: 1fr; }
}
</style>
