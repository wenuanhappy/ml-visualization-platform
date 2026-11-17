<template>
  <section class="case-library-card">
    <header class="panel-header">
      <h3>实验案例库</h3>
    </header>

    <div class="library-copy">
      <div>
        <div class="library-title">引导式工作流程</div>
        <p class="library-subtitle">{{ guidance.headline }}</p>
      </div>
      <div
        class="phase-chip"
        :class="{ running: guidance.phase === 'running', verified: guidance.phase === 'verified' }"
      >
        {{ guidance.phase }}
      </div>
    </div>

    <div class="step-rail">
      <div
        v-for="(label, index) in phaseLabels"
        :key="label"
        class="step-item"
        :class="{ active: index <= guidance.activeStep }"
      >
        <span>{{ index + 1 }}</span>
        <small>{{ label }}</small>
      </div>
    </div>

    <div class="case-grid">
      <button
        v-for="item in cases"
        :key="item.id"
        class="case-card"
        :class="{ active: item.id === activeCaseId }"
        @click="$emit('load-case', item.id)"
      >
        <div class="case-topline">
          <strong>{{ item.title }}</strong>
          <span>{{ item.algorithmName }}</span>
        </div>
        <p>{{ item.summary }}</p>
        <div class="case-meta">{{ item.datasetName }} · {{ item.category }}</div>
        <div class="case-checks">
          <div v-for="objective in item.objectives" :key="objective">{{ objective }}</div>
        </div>
      </button>
    </div>

    <div v-if="activeCase" class="validation-box">
      <div class="validation-title">验证目标</div>
      <p>{{ activeCase.validationText }}</p>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { GUIDED_CASES, PHASE_LABELS, caseById } from '@/services/cases'

const props = defineProps({
  activeCaseId: { type: String, default: '' },
  guidance: { type: Object, default: () => ({ phase: 'idle', activeStep: 0, headline: '' }) }
})

defineEmits(['load-case'])

const cases = GUIDED_CASES
const phaseLabels = PHASE_LABELS
const activeCase = computed(() => caseById(props.activeCaseId))
</script>

<style scoped>
.case-library-card {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
}

.panel-header h3 {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.library-copy {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.library-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.library-subtitle {
  margin: 4px 0 0;
  font-size: 11px;
  color: #64748b;
}

.phase-chip {
  flex: none;
  padding: 3px 10px;
  font-size: 10px;
  font-weight: 600;
  text-transform: capitalize;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 999px;
}

.phase-chip.running {
  color: #0369a1;
  background: #e0f2fe;
}

.phase-chip.verified {
  color: #047857;
  background: #d1fae5;
}

.step-rail {
  display: flex;
  gap: 6px;
  margin-bottom: 14px;
}

.step-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 7px 4px;
  background: #f8fafc;
  border-radius: 9px;
  color: #94a3b8;
}

.step-item span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  font-size: 9px;
  color: #fff;
  background: #cbd5e1;
  border-radius: 50%;
}

.step-item small {
  font-size: 9px;
}

.step-item.active {
  color: #0369a1;
  background: #eef6ff;
}

.step-item.active span {
  background: #378add;
}

.case-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 10px;
}

.case-card {
  padding: 11px 13px;
  text-align: left;
  background: #f8fafc;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: all 160ms ease;
}

.case-card:hover {
  border-color: rgba(55, 138, 221, 0.4);
  background: #f2f7ff;
}

.case-card.active {
  border-color: #378add;
  background: #eef6ff;
}

.case-topline {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.case-topline strong {
  font-size: 12px;
  color: #0f172a;
}

.case-topline span {
  font-size: 9px;
  color: #94a3b8;
}

.case-card p {
  margin: 5px 0;
  font-size: 11px;
  line-height: 1.5;
  color: #64748b;
}

.case-meta {
  font-size: 10px;
  color: #94a3b8;
}

.case-checks {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.case-checks div {
  font-size: 10px;
  color: #64748b;
}

.case-checks div::before {
  content: '· ';
}

.validation-box {
  margin-top: 12px;
  padding: 10px 12px;
  background: #f4f7fd;
  border-radius: 10px;
}

.validation-title {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
  margin-bottom: 4px;
}

.validation-box p {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: #475569;
}
</style>
