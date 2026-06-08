<script setup>
import logoUrl from '../assets/cs_logo_2025_blue.png';

defineProps({
  pageTitle: {
    type: String,
    default: '参数配置'
  }
});

const steps = [
  { key: 'config', label: '参数配置', icon: '⚙️' },
  { key: 'monitor', label: '实时监控', icon: '📊' },
  { key: 'report', label: '结果分析', icon: '📋' }
];
</script>

<template>
  <header class="brand-header">
    <section class="brand-inner">
      <img class="brand-logo" :src="logoUrl" alt="北京交通大学与计算机科学与技术学院标识" />

      <div class="brand-meta">
        <div class="meta-top">
          <span class="badge"><span class="status-dot"></span> Dining Simulation System</span>
        </div>
        <h1>北京交通大学就餐仿真系统</h1>
      </div>

      <nav class="step-nav">
        <div
          v-for="(step, index) in steps"
          :key="step.key"
          :class="['step-item', { active: pageTitle === step.label, passed: steps.findIndex(s => s.label === pageTitle) > index }]"
        >
          <div class="step-circle">
            <span class="step-icon">{{ step.icon }}</span>
          </div>
          <span class="step-label">{{ step.label }}</span>
          <div v-if="index < steps.length - 1" class="step-connector"></div>
        </div>
      </nav>
    </section>
  </header>
</template>

<style scoped>
.brand-header {
  width: min(1680px, calc(100vw - 80px));
  margin: 0 auto;
}

.brand-inner {
  display: grid;
  grid-template-columns: 380px 1fr auto;
  align-items: center;
  gap: 32px;
  padding: 22px 32px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 20px 52px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(20px);
}

.brand-logo {
  width: 100%;
  max-height: 88px;
  object-fit: contain;
  filter: drop-shadow(0 8px 20px rgba(0, 64, 152, 0.15));
}

.meta-top {
  margin-bottom: 10px;
}

.brand-meta h1 {
  margin: 0 0 8px;
  color: var(--bjtu-blue-dark);
  font-size: 28px;
  letter-spacing: 0.04em;
  font-weight: 900;
}

.brand-meta p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  letter-spacing: 0.02em;
}

/* ── Step navigation ── */
.step-nav {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 14px 20px;
  border-radius: 20px;
  background: rgba(0, 64, 152, 0.05);
  border: 1px solid rgba(0, 64, 152, 0.1);
}

.step-item {
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
}

.step-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  border: 2px solid rgba(0, 64, 152, 0.18);
  background: rgba(255, 255, 255, 0.8);
  transition: all 0.28s ease;
  flex-shrink: 0;
}

.step-icon {
  font-size: 16px;
  line-height: 1;
}

.step-label {
  font-size: 13px;
  font-weight: 800;
  color: #94a3b8;
  transition: color 0.28s ease;
  white-space: nowrap;
}

.step-connector {
  width: 32px;
  height: 2px;
  background: rgba(0, 64, 152, 0.14);
  margin: 0 10px;
  flex-shrink: 0;
  border-radius: 999px;
}

/* active step */
.step-item.active .step-circle {
  background: linear-gradient(135deg, #004098, #0b63ce);
  border-color: transparent;
  box-shadow: 0 8px 20px rgba(0, 64, 152, 0.32);
}

.step-item.active .step-label {
  color: var(--bjtu-blue-dark);
}

/* passed step */
.step-item.passed .step-circle {
  background: rgba(22, 163, 74, 0.12);
  border-color: rgba(22, 163, 74, 0.3);
}

.step-item.passed .step-label {
  color: #16a34a;
}

.step-item.passed .step-connector {
  background: rgba(22, 163, 74, 0.3);
}

@media (max-width: 1400px) {
  .brand-inner {
    grid-template-columns: 320px 1fr auto;
    gap: 24px;
    padding: 18px 24px;
  }

  .brand-meta h1 { font-size: 24px; }
}
</style>
