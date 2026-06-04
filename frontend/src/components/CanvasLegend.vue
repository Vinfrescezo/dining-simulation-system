<script setup>
const items = [
  { label: '寻路 / 移动', color: '#2563eb' },
  { label: '排队', color: '#f97316' },
  { label: '打饭', color: '#7c3aed' },
  { label: '等座', color: '#be185d' },
  { label: '就餐', color: '#16a34a' },
  { label: '离开', color: '#64748b' }
];
</script>

<template>
  <div class="legend">
    <span v-for="item in items" :key="item.label">
      <i :style="{ background: item.color }"></i>{{ item.label }}
    </span>
  </div>
</template>

<style scoped>
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}

.legend i {
  width: 9px;
  height: 9px;
  border-radius: 999px;
}
</style>
