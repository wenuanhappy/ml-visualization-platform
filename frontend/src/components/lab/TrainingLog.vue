<template>
  <section class="training-log-card">
    <header class="log-header">
      <h3>Training Log</h3>
      <span class="status-dot" :class="{ running: status === 'training', done: status === 'completed' }"></span>
    </header>

    <div v-if="entries.length" class="log-stream">
      <div
        v-for="(entry, index) in entries"
        :key="index"
        class="log-entry"
        :class="{ success: entry.tone === 'success', warning: entry.tone === 'warning' }"
      >
        <span class="log-step">S{{ entry.step }}</span>
        <span class="log-text">{{ entry.text }}</span>
      </div>
    </div>

    <p v-else class="empty-log">Load a case and start training to see step-by-step guidance.</p>
  </section>
</template>

<script setup>
defineProps({
  entries: { type: Array, default: () => [] },
  status: { type: String, default: 'idle' }
})
</script>

<style scoped>
.training-log-card {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
}

.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.log-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #cbd5e1;
}

.status-dot.running {
  background: #38bdf8;
  box-shadow: 0 0 0 4px rgba(56, 189, 248, 0.18);
  animation: pulse 1.4s ease-in-out infinite;
}

.status-dot.done {
  background: #10b981;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.log-stream {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  overflow-y: auto;
}

.log-entry {
  display: flex;
  gap: 8px;
  font-size: 11px;
  line-height: 1.5;
  color: #64748b;
}

.log-entry.success .log-text {
  color: #0f766e;
}

.log-entry.warning .log-text {
  color: #b45309;
}

.log-step {
  flex: none;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #94a3b8;
}

.log-text {
  flex: 1;
}

.empty-log {
  margin: 0;
  font-size: 11px;
  color: #94a3b8;
}
</style>
