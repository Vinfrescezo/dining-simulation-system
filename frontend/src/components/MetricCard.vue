<script setup>
defineProps({
  label: String,
  value: [String, Number],
  unit: String,
  hint: String,
  tone: {
    type: String,
    default: 'blue'
  }
});
</script>

<template>
  <article :class="['metric-card', `tone-${tone}`]">
    <p>{{ label }}</p>
    <div>
      <strong>{{ value }}</strong>
      <span v-if="unit">{{ unit }}</span>
    </div>
    <small v-if="hint">{{ hint }}</small>
  </article>
</template>

<style scoped>
.metric-card {
  min-height: 112px;
  padding: 18px;
  border-radius: 22px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.07);
}

.metric-card p {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.metric-card div {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.metric-card strong {
  font-size: 32px;
  line-height: 1;
  color: var(--bjtu-blue-dark);
}

.metric-card span {
  color: var(--muted);
  font-weight: 800;
}

.metric-card small {
  display: block;
  margin-top: 10px;
  color: #64748b;
}

.tone-green strong { color: var(--green); }
.tone-red strong { color: var(--red); }
.tone-orange strong { color: var(--orange); }
.tone-purple strong { color: var(--purple); }
</style>
