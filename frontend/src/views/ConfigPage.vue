<script setup>
import { reactive, ref } from 'vue';
import logoUrl from '../assets/cs_logo_2025_blue.png';
import { DEFAULT_CONFIG } from '../utils/constants';
import { toPayload, validateConfig } from '../utils/validators';
import { startSimulation } from '../services/api';

const emit = defineEmits(['started']);

const PRESETS = [
  {
    key: 'minghu',
    name: '明湖餐厅',
    desc: '北交大最大餐厅，三层楼面、窗口众多，午高峰人流密集',
    studentCount: 2000,
    windowCount: 14,
    seatCount: 400,
    simDurationTick: 5400,
    maxQueueCapacity: 20,
    orderingTime: 28,
    eatingTime: 480
  },
  {
    key: 'xuehuo',
    name: '学活餐厅',
    desc: '学生活动中心内餐厅，规模中等，品类丰富，用餐高峰集中',
    studentCount: 1200,
    windowCount: 10,
    seatCount: 240,
    simDurationTick: 5400,
    maxQueueCapacity: 20,
    orderingTime: 28,
    eatingTime: 480
  },
  {
    key: 'xuesi',
    name: '学四餐厅',
    desc: '紧邻学四宿舍，面积较小、窗口有限，早晚餐较为拥挤',
    studentCount: 800,
    windowCount: 6,
    seatCount: 140,
    simDurationTick: 5400,
    maxQueueCapacity: 18,
    orderingTime: 32,
    eatingTime: 420
  },
  {
    key: 'liuyuan',
    name: '留园餐厅',
    desc: '留学生公寓旁，规模最小，以特色档口为主，日常人流较少',
    studentCount: 500,
    windowCount: 4,
    seatCount: 80,
    simDurationTick: 5400,
    maxQueueCapacity: 15,
    orderingTime: 35,
    eatingTime: 540
  }
];

const activePreset = ref('xuehuo');

const form = reactive({
  studentCount: DEFAULT_CONFIG.studentCount,
  windowCount: DEFAULT_CONFIG.windowCount,
  simDurationTick: DEFAULT_CONFIG.simDurationTick,
  seatCount: DEFAULT_CONFIG.seatCount,
  maxQueueCapacity: DEFAULT_CONFIG.maxQueueCapacity,
  orderingTime: DEFAULT_CONFIG.orderingTime,
  eatingTime: DEFAULT_CONFIG.eatingTime,
  renderThreshold: DEFAULT_CONFIG.renderThreshold,
  mode: 'local-first'
});

function applyPreset(preset) {
  activePreset.value = preset.key;
  form.studentCount = preset.studentCount;
  form.windowCount = preset.windowCount;
  form.seatCount = preset.seatCount;
  form.simDurationTick = preset.simDurationTick;
  form.maxQueueCapacity = preset.maxQueueCapacity;
  form.orderingTime = preset.orderingTime;
  form.eatingTime = preset.eatingTime;
}

applyPreset(PRESETS.find(p => p.key === 'xuehuo'));

const errors = reactive({});
const loading = ref(false);
const backendHint = ref('');

function setErrors(nextErrors) {
  Object.keys(errors).forEach(key => delete errors[key]);
  Object.assign(errors, nextErrors);
}

async function submit() {
  const config = toPayload(form);
  const preset = PRESETS.find(p => p.key === activePreset.value);
  if (preset) config.sceneName = preset.name;
  const result = validateConfig(config);
  setErrors(result.errors);
  if (!result.valid) return;

  // ── 本地模式 ──
  if (form.mode === 'local') {
    emit('started', { config, useLocalFallback: true });
    return;
  }

  // ── 后端 / 自动模式：在配置页等待后端跑完仿真 ──
  loading.value = true;
  backendHint.value = '';
  try {
    const serverData = await startSimulation(config);
    emit('started', { config, useLocalFallback: false, serverData });
  } catch (err) {
    if (form.mode === 'local-first') {
      backendHint.value = '未连接到后端，已自动切换本地演示模式。';
      emit('started', { config, useLocalFallback: true });
    } else {
      backendHint.value = '后端不可用，请确认 Spring Boot 已启动。';
    }
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="page-card config-page">
    <img class="watermark" :src="logoUrl" alt="北京交通大学计算机学院校徽水印" />

    <div class="hero-copy">
      <div class="hero-badge-row">
        <span class="badge"><span class="status-dot"></span> 参数初始化</span>
        <span class="hero-tag">BJTU · 2025</span>
      </div>
      <h2>就餐场景<br/>仿真配置</h2>

      <div class="preset-section">
        <div class="preset-label">选择食堂场景</div>
        <div class="preset-cards">
          <button
            v-for="p in PRESETS"
            :key="p.key"
            class="preset-card"
            :class="{ active: activePreset === p.key }"
            @click="applyPreset(p)"
          >
            <strong>{{ p.name }}</strong>
            <span class="preset-meta">{{ p.windowCount }}窗口 · {{ p.seatCount }}座 · {{ p.studentCount }}人</span>
          </button>
        </div>
      </div>
    </div>

    <form class="config-form" @submit.prevent="submit">
      <div class="form-header">
        <h3>基础参数配置</h3>
      </div>

      <div class="grid-2">
        <div class="field">
          <label for="studentCount">预计下课人数</label>
          <input id="studentCount" v-model.number="form.studentCount" type="number" min="1" max="4999" />
          <small>进入食堂仿真的学生总量，范围 1—4999。</small>
          <span class="error">{{ errors.studentCount }}</span>
        </div>

        <div class="field">
          <label for="windowCount">开放窗口数量</label>
          <input id="windowCount" v-model.number="form.windowCount" type="number" min="1" max="20" />
          <small>参与服务的打饭窗口数，窗口越多排队压力通常越低。</small>
          <span class="error">{{ errors.windowCount }}</span>
        </div>

        <div class="field">
          <label for="simDurationTick">模拟时长（秒/Tick）</label>
          <input id="simDurationTick" v-model.number="form.simDurationTick" type="number" min="60" max="10800" />
          <small>1 Tick ≈ 1 秒，例如 1200 表示约 20 分钟。</small>
          <span class="error">{{ errors.simDurationTick }}</span>
        </div>

        <div class="field">
          <label for="seatCount">座位数量</label>
          <input id="seatCount" v-model.number="form.seatCount" type="number" min="20" max="1200" />
          <small>总座位数，画布按四人桌自动摆放。</small>
          <span class="error">{{ errors.seatCount }}</span>
        </div>
      </div>

      <details class="advanced-section">
        <summary>高级参数 <span>（可选）</span></summary>
        <div class="advanced-grid">
          <div class="field">
            <label for="maxQueueCapacity">单窗口最大队列</label>
            <input id="maxQueueCapacity" v-model.number="form.maxQueueCapacity" type="number" min="5" max="80" />
            <small>超过后学生计入流失。</small>
          </div>
          <div class="field">
            <label for="orderingTime">打饭时长（秒/人）</label>
            <input id="orderingTime" v-model.number="form.orderingTime" type="number" min="5" max="120" />
            <small>单个学生在窗口打饭的平均耗时。</small>
            <span class="error">{{ errors.orderingTime }}</span>
          </div>
          <div class="field">
            <label for="eatingTime">用餐时长（秒/人）</label>
            <input id="eatingTime" v-model.number="form.eatingTime" type="number" min="60" max="1800" />
            <small>学生坐下后的平均就餐时长，影响座位周转。</small>
            <span class="error">{{ errors.eatingTime }}</span>
          </div>
          <div class="field">
            <label for="renderThreshold">渲染阈值</label>
            <input id="renderThreshold" v-model.number="form.renderThreshold" type="number" min="50" max="1000" />
            <small>人数超过该值时切换圆点渲染。</small>
          </div>
          <div class="field">
            <label for="mode">运行模式</label>
            <select id="mode" v-model="form.mode">
              <option value="local-first">自动（优先后端）</option>
              <option value="backend">仅后端</option>
              <option value="local">本地演示</option>
            </select>
            <small>本地演示无需启动后端服务。</small>
          </div>
        </div>
      </details>

      <p v-if="backendHint" class="backend-hint">{{ backendHint }}</p>

      <button class="primary-btn submit-btn" :disabled="loading" type="submit">
        <span v-if="!loading">▶ 开始仿真</span>
        <span v-else class="loading-row">
          <span class="btn-spinner"></span> 后端仿真运算中…
        </span>
      </button>
    </form>
  </section>
</template>

<style scoped>
.config-page {
  display: grid;
  grid-template-columns: 0.78fr 1.22fr;
  gap: 32px;
  min-height: 680px;
  padding: 52px 48px;
}

.watermark {
  position: absolute;
  left: 24px;
  bottom: 20px;
  width: 560px;
  opacity: 0.04;
  filter: grayscale(0.2);
  pointer-events: none;
}

/* ── Hero copy ── */
.hero-copy {
  align-self: center;
  padding: 8px 12px 8px 4px;
}

.hero-badge-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}

.hero-tag {
  font-size: 11px;
  font-weight: 900;
  color: #94a3b8;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.hero-copy h2 {
  margin: 0 0 14px;
  color: var(--bjtu-blue-dark);
  font-size: 52px;
  line-height: 1.08;
  letter-spacing: -0.03em;
  font-weight: 900;
}

.hero-copy p {
  max-width: 440px;
  margin: 0 0 24px;
  color: #475569;
  font-size: 15px;
  line-height: 1.8;
}

/* ── Preset cards ── */
.preset-section {
  margin-top: 4px;
}

.preset-label {
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 12px;
}

.preset-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.preset-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 2px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.85);
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.preset-card:hover {
  border-color: rgba(0, 64, 152, 0.25);
  background: rgba(0, 64, 152, 0.04);
}

.preset-card.active {
  border-color: var(--bjtu-blue-dark);
  background: rgba(0, 64, 152, 0.07);
  box-shadow: 0 4px 16px rgba(0, 64, 152, 0.12);
}

.preset-card strong {
  font-size: 15px;
  font-weight: 900;
  color: var(--bjtu-blue-dark);
}

.preset-card .preset-meta {
  font-size: 11px;
  font-weight: 800;
  color: #0b63ce;
  letter-spacing: 0.02em;
}

.preset-card small {
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
  font-weight: 600;
}

/* ── Config form ── */
.config-form {
  align-self: center;
  padding: 36px 32px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.97);
  border: 1.5px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.09);
}

.form-header {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 24px;
}

.config-form h3 {
  margin: 0;
  color: var(--bjtu-blue-dark);
  font-size: 22px;
  font-weight: 900;
}

.form-subtitle {
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.field small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

/* ── Advanced section ── */
.advanced-section {
  margin-top: 22px;
  border-radius: 18px;
  border: 1.5px solid rgba(0, 64, 152, 0.1);
  overflow: hidden;
}

.advanced-section summary {
  padding: 14px 18px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 800;
  color: var(--bjtu-blue-dark);
  background: rgba(0, 64, 152, 0.04);
  list-style: none;
  user-select: none;
}

.advanced-section summary::-webkit-details-marker { display: none; }

.advanced-section summary span {
  color: var(--muted);
  font-weight: 600;
  font-size: 13px;
}

.advanced-section[open] summary {
  border-bottom: 1.5px solid rgba(0, 64, 152, 0.1);
}

.advanced-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 18px;
}

.advanced-grid .field input,
.advanced-grid .field select {
  height: 44px;
  font-size: 14px;
}

.backend-hint {
  margin: 18px 0 0;
  padding: 14px 16px;
  border-radius: 16px;
  color: #92400e;
  background: #fffbeb;
  border: 1.5px solid #fde68a;
  font-size: 13px;
  font-weight: 700;
}

.submit-btn {
  width: 100%;
  min-height: 56px;
  margin-top: 24px;
  font-size: 17px;
  letter-spacing: 0.04em;
  border-radius: 18px;
}

.loading-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.btn-spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 3px solid rgba(255,255,255,0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }
</style>
