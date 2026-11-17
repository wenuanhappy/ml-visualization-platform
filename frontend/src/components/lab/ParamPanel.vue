<template>
  <section class="param-panel-card">
    <header class="panel-header">
      <h3>超参数面板</h3>
      <button type="button" class="reset-defaults-button" :disabled="!algorithm" @click="$emit('reset-defaults')">
        恢复默认值
      </button>
    </header>

    <div v-if="algorithm" class="param-list">
      <div v-for="param in algorithm.parameters" :key="param.name" class="param-item">
        <div class="param-label">
          {{ param.displayName }}
          <span
            v-if="param.min !== undefined && param.max !== undefined"
            class="info-icon"
            :title="`${param.min} ~ ${param.max}`"
          >i</span>
        </div>

        <div v-if="param.type === 'number'" class="param-input">
          <input
            type="range"
            :min="param.min || 0"
            :max="param.max || 100"
            :step="param.step || 1"
            :value="parameterValues[param.name]"
            @input="$emit('parameter-change', { paramName: param.name, value: Number($event.target.value) })"
          />
          <span class="param-value">{{ formatParameterValue(param, parameterValues[param.name]) }}</span>
        </div>

        <select
          v-else-if="param.type === 'select'"
          class="param-select"
          :value="parameterValues[param.name]"
          @change="$emit('parameter-change', { paramName: param.name, value: $event.target.value })"
        >
          <option v-for="option in param.options" :key="option" :value="option">{{ option }}</option>
        </select>
      </div>
    </div>

    <div v-else class="empty-message">请先选择算法以解锁参数调整。</div>
  </section>
</template>

<script setup>
import { formatParameterValue } from '@/services/algorithms'

defineProps({
  algorithm: { type: Object, default: null },
  parameterValues: { type: Object, default: () => ({}) }
})

defineEmits(['parameter-change', 'reset-defaults'])
</script>

<style scoped>
.param-panel-card {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.reset-defaults-button {
  padding: 4px 8px;
  font-size: 11px;
  color: #378add;
  background: transparent;
  border: none;
  cursor: pointer;
}

.reset-defaults-button:disabled {
  color: #cbd5e1;
  cursor: not-allowed;
}

.param-item {
  margin-bottom: 16px;
}

.param-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #475569;
  margin-bottom: 6px;
}

.info-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 13px;
  height: 13px;
  font-size: 9px;
  color: #94a3b8;
  border: 1px solid #cbd5e1;
  border-radius: 50%;
  cursor: help;
}

.param-input {
  display: flex;
  align-items: center;
  gap: 10px;
}

.param-input input[type='range'] {
  flex: 1;
  accent-color: #378add;
}

.param-value {
  min-width: 48px;
  text-align: right;
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.param-select {
  width: 100%;
  padding: 6px 10px;
  font-size: 12px;
  color: #0f172a;
  background: #fbfcfe;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
}

.empty-message {
  font-size: 12px;
  color: #94a3b8;
}
</style>
