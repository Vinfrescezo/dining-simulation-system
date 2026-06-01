<script setup>
import { computed, reactive } from 'vue';
import BrandHeader from './components/BrandHeader.vue';
import ConfigPage from './views/ConfigPage.vue';
import MonitorPage from './views/MonitorPage.vue';
import ReportPage from './views/ReportPage.vue';

const state = reactive({
  page: 'config',
  config: null,
  simId: '',
  report: null,
  useLocalFallback: true
});

const pageTitle = computed(() => ({
  config: '参数配置',
  monitor: '实时监控',
  report: '结果分析'
}[state.page]));

function handleStarted(payload) {
  state.config = payload.config;
  state.simId = payload.simId;
  state.useLocalFallback = payload.useLocalFallback;
  state.report = null;
  state.page = 'monitor';
}

function handleFinished(report) {
  state.report = report;
  state.page = 'report';
}

function restart() {
  state.page = 'config';
  state.report = null;
  state.simId = '';
}

function backToMonitor() {
  if (state.config) state.page = 'monitor';
}
</script>

<template>
  <main class="app-shell">
    <BrandHeader :page-title="pageTitle" />

    <ConfigPage
      v-if="state.page === 'config'"
      @started="handleStarted"
    />

    <MonitorPage
      v-else-if="state.page === 'monitor'"
      :config="state.config"
      :sim-id="state.simId"
      :use-local-fallback="state.useLocalFallback"
      @finished="handleFinished"
      @restart="restart"
    />

    <ReportPage
      v-else
      :report="state.report"
      :config="state.config"
      @restart="restart"
      @back="backToMonitor"
    />
  </main>
</template>
