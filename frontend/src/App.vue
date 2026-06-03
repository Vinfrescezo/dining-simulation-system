<script setup>
import { computed, reactive } from 'vue';
import BrandHeader from './components/BrandHeader.vue';
import ConfigPage from './views/ConfigPage.vue';
import MonitorPage from './views/MonitorPage.vue';
import ReportPage from './views/ReportPage.vue';

const state = reactive({
  page: 'config',
  config: null,
  report: null,
  useLocalFallback: true,
  serverData: null
});

const pageTitle = computed(() => ({
  config: '参数配置',
  monitor: '实时监控',
  report: '结果分析'
}[state.page]));

function handleStarted(payload) {
  state.config = payload.config;
  state.useLocalFallback = payload.useLocalFallback ?? true;
  state.serverData = payload.serverData ?? null;
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
  state.serverData = null;
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
      :use-local-fallback="state.useLocalFallback"
      :server-data="state.serverData"
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
