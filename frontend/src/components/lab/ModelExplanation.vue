<template>
  <section class="explanation-card">
    <header class="panel-header">
      <h3>模型解释</h3>
    </header>

    <template v-if="explanation">
      <div class="explanation-header">
        <div>
          <div class="explanation-title">{{ explanation.title }}</div>
          <p class="explanation-description">{{ explanation.description }}</p>
        </div>
        <div v-if="algorithm" class="algorithm-chip">{{ algorithm.displayName }}</div>
      </div>

      <div v-if="explanation.mode === 'tree'" class="tree-panel">
        <svg viewBox="0 0 380 300" class="tree-svg">
          <g v-for="(edge, index) in explanation.treeEdges" :key="index">
            <line
              class="tree-edge"
              :x1="nodeX(edge.from) + 42"
              :y1="nodeY(edge.from) + 42"
              :x2="nodeX(edge.to) + 42"
              :y2="nodeY(edge.to) + 42"
            ></line>
          </g>
          <g v-for="node in explanation.treeNodes" :key="node.id">
            <foreignObject :x="node.x" :y="node.y" width="100" height="90">
              <div class="tree-node" :class="node.tone">
                <strong>{{ node.label }}</strong>
                <span>{{ node.detail }}</span>
              </div>
            </foreignObject>
          </g>
        </svg>
      </div>

      <div v-else-if="explanation.mode === 'weights'" class="weights-panel">
        <div v-for="weight in explanation.weights" :key="weight.feature" class="weight-row">
          <div class="weight-label">{{ weight.feature }}</div>
          <div class="weight-bar-track">
            <div
              class="weight-bar"
              :class="{ negative: weight.value < 0 }"
              :style="{ width: `${weightWidth(weight.value)}%` }"
            ></div>
          </div>
          <div class="weight-value">{{ weight.value.toFixed(2) }}</div>
        </div>
      </div>

      <div v-else class="summary-panel">
        <div v-for="note in explanation.notes" :key="note" class="summary-note">{{ note }}</div>
      </div>

      <div class="note-stack">
        <div class="note-title">解释说明</div>
        <div v-for="note in explanation.notes" :key="note" class="note-item">{{ note }}</div>
      </div>
    </template>

    <p v-else class="empty-text">选择算法并开始训练以解锁解释视图。</p>
  </section>
</template>

<script setup>
const props = defineProps({
  algorithm: { type: Object, default: null },
  explanation: { type: Object, default: null }
})

function nodeX(id) {
  return props.explanation?.treeNodes?.find(node => node.id === id)?.x || 0
}

function nodeY(id) {
  return props.explanation?.treeNodes?.find(node => node.id === id)?.y || 0
}

function weightWidth(value) {
  return Math.max(8, Math.abs(value) * 100)
}
</script>

<style scoped>
.explanation-card {
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

.explanation-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.explanation-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.explanation-description {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: #64748b;
}

.algorithm-chip {
  flex: none;
  padding: 3px 9px;
  font-size: 10px;
  color: #378add;
  background: rgba(55, 138, 221, 0.1);
  border-radius: 999px;
}

.tree-svg {
  width: 100%;
  height: auto;
}

.tree-edge {
  stroke: rgba(148, 163, 184, 0.55);
  stroke-width: 1.2;
}

.tree-node {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  font-size: 9px;
  color: #334155;
  background: #f8fafc;
  border-left: 2px solid #94a3b8;
  border-radius: 6px;
  line-height: 1.35;
}

.tree-node.cyan {
  border-left-color: #38bdf8;
}

.tree-node.purple {
  border-left-color: #a78bfa;
}

.tree-node.green {
  border-left-color: #34d399;
}

.weight-row {
  display: grid;
  grid-template-columns: 76px 1fr 42px;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 11px;
  color: #475569;
}

.weight-bar-track {
  height: 7px;
  background: rgba(148, 163, 184, 0.16);
  border-radius: 999px;
  overflow: hidden;
}

.weight-bar {
  height: 100%;
  background: linear-gradient(90deg, #378add, #38bdf8);
  border-radius: 999px;
}

.weight-bar.negative {
  background: linear-gradient(90deg, #f472b6, #fb7185);
}

.weight-value {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: #0f172a;
}

.summary-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-note {
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.5;
  color: #475569;
  background: #f8fafc;
  border-radius: 8px;
}

.note-stack {
  margin-top: 12px;
  border-top: 1px solid rgba(226, 232, 240, 0.8);
  padding-top: 10px;
}

.note-title {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
  margin-bottom: 6px;
}

.note-item {
  font-size: 11px;
  line-height: 1.6;
  color: #64748b;
}

.empty-text {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
