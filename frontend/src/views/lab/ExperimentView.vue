<template>
  <div class="lab">
    <section class="toolbar">
      <label class="field">
        <span>算法</span>
        <select v-model="algorithmId" @change="onAlgorithmChange">
          <option v-for="item in algorithms" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>

      <label class="field">
        <span>数据集</span>
        <select v-model="datasetId" @change="onDatasetChange">
          <option v-for="item in datasets" :key="item.id" :value="item.id">
            {{ item.name }}（{{ item.pointCount }} 条）
          </option>
        </select>
      </label>

      <div class="buttons">
        <button class="btn primary" :disabled="busy || !dataset" @click="startTraining">开始</button>
        <button class="btn" :disabled="busy || !session || finished" @click="singleStep">单步</button>
        <button class="btn" :disabled="busy || !session" @click="resetTraining">重置</button>
      </div>

      <div class="stats">
        <span>步数 <strong>{{ currentStep }} / {{ totalSteps || '—' }}</strong></span>
        <span>{{ scoreLabel }} <strong>{{ formatNumber(score) }}</strong></span>
        <span>损失 <strong>{{ formatNumber(loss) }}</strong></span>
      </div>
    </section>

    <p v-if="notice" class="notice">{{ notice }}</p>

    <VisualizationCanvas
      :dataset="dataset"
      :algorithm-type="algorithmType"
      :model="model"
      :lines="overlayLines"
      :projection-note="projectionNote"
      @axes-change="onAxesChange"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import VisualizationCanvas from '@/components/lab/VisualizationCanvas.vue'
import { createSession, getDataset, getDatasets, resetSession, stepSession } from '@/api/ml'

const algorithms = [
  { id: 'linear_regression', name: '线性回归', type: 'regression' },
  { id: 'logistic_regression', name: '逻辑回归', type: 'classification' },
  { id: 'support_vector_machine', name: '支持向量机', type: 'classification' },
  { id: 'k_means', name: 'K-Means', type: 'clustering' },
  { id: 'pca', name: 'PCA', type: 'dimension' },
  { id: 'random_forest', name: '随机森林', type: 'classification' },
  { id: 'decision_tree', name: '决策树', type: 'classification' }
]

const algorithmId = ref('linear_regression')
const datasetId = ref(null)
const datasets = ref([])
const dataset = ref(null)
const session = ref(null)
const busy = ref(false)
const notice = ref('')
const axes = ref({ x: null, y: null })

const algorithmType = computed(
  () => algorithms.find(item => item.id === algorithmId.value)?.type ?? 'regression'
)
const model = computed(() => session.value?.modelData ?? {})
const currentStep = computed(() => session.value?.currentStep ?? 0)
const totalSteps = computed(() => session.value?.totalSteps ?? 0)
const loss = computed(() => session.value?.loss ?? null)
const score = computed(() => session.value?.accuracy ?? null)
const scoreLabel = computed(() => session.value?.scoreLabel ?? '得分')
const finished = computed(() => session.value?.status === 'finished')

const projectionNote = computed(() => {
  if (algorithmId.value !== 'pca') return ''
  return 'PCA 视图：散点坐标为主成分投影，颜色保留原始类别。'
})

/** 回归线 / 分类边界直接来自后端；决策树按已展开的分裂现算线段 */
const overlayLines = computed(() => {
  if (algorithmId.value === 'pca' || algorithmId.value === 'k_means') {
    return []
  }
  if (algorithmId.value === 'decision_tree') {
    return buildTreeSegments()
  }
  const line = model.value.regressionLine
  return line && line.length === 2 ? [line] : []
})

function onAxesChange(next) {
  axes.value = next
}

function featureIndex(name) {
  return (dataset.value?.featureNames ?? []).indexOf(name)
}

function buildTreeSegments() {
  const nodes = model.value.tree ?? []
  const revealed = model.value.revealedSplits ?? 0
  if (!nodes.length || revealed <= 0) return []

  const points = dataset.value?.points ?? []
  const xIdx = featureIndex(axes.value.x)
  const yIdx = featureIndex(axes.value.y)
  if (xIdx < 0 || yIdx < 0) return []

  const xs = points.map(point => point.features[xIdx] ?? 0)
  const ys = points.map(point => point.features[yIdx] ?? 0)
  let partitions = [
    { nodeId: 0, x1: Math.min(...xs), x2: Math.max(...xs), y1: Math.min(...ys), y2: Math.max(...ys) }
  ]
  const segments = []

  const splits = nodes
    .filter(node => node.splitOrder >= 0 && node.splitOrder < revealed)
    .sort((a, b) => a.splitOrder - b.splitOrder)

  for (const split of splits) {
    const index = partitions.findIndex(item => item.nodeId === split.id)
    if (index < 0) continue
    const part = partitions[index]
    if (split.featureIndex === xIdx) {
      segments.push([[split.threshold, part.y1], [split.threshold, part.y2]])
      partitions.splice(
        index,
        1,
        { nodeId: split.leftId, x1: part.x1, x2: split.threshold, y1: part.y1, y2: part.y2 },
        { nodeId: split.rightId, x1: split.threshold, x2: part.x2, y1: part.y1, y2: part.y2 }
      )
    } else if (split.featureIndex === yIdx) {
      segments.push([[part.x1, split.threshold], [part.x2, split.threshold]])
      partitions.splice(
        index,
        1,
        { nodeId: split.leftId, x1: part.x1, x2: part.x2, y1: part.y1, y2: split.threshold },
        { nodeId: split.rightId, x1: part.x1, x2: part.x2, y1: split.threshold, y2: part.y2 }
      )
    }
  }
  return segments
}

function formatNumber(value) {
  if (value == null || Number.isNaN(Number(value))) return '—'
  return Number(value).toFixed(4)
}

function notify(text) {
  notice.value = text
  setTimeout(() => {
    if (notice.value === text) notice.value = ''
  }, 3000)
}

async function loadDatasets() {
  const response = await getDatasets()
  datasets.value = response.data ?? []
  if (datasets.value.length) {
    datasetId.value = datasets.value[0].id
    await loadDataset(datasetId.value)
  }
}

async function loadDataset(id) {
  const response = await getDataset(id)
  dataset.value = response.data
  session.value = null
}

async function onDatasetChange() {
  await loadDataset(datasetId.value)
}

async function onAlgorithmChange() {
  session.value = null
}

async function ensureSession() {
  if (session.value) return
  const response = await createSession({
    algorithm: algorithmId.value,
    datasetId: datasetId.value,
    parameters: {}
  })
  session.value = response.data
}

async function startTraining() {
  busy.value = true
  try {
    await ensureSession()
    while (session.value && session.value.status !== 'finished') {
      const response = await stepSession(session.value.sessionId)
      session.value = response.data
      await new Promise(resolve => setTimeout(resolve, 16))
    }
  } catch (error) {
    notify('训练失败：' + error.message)
  } finally {
    busy.value = false
  }
}

async function singleStep() {
  busy.value = true
  try {
    await ensureSession()
    const response = await stepSession(session.value.sessionId)
    session.value = response.data
  } catch (error) {
    notify('单步失败：' + error.message)
  } finally {
    busy.value = false
  }
}

async function resetTraining() {
  if (!session.value) return
  busy.value = true
  try {
    const response = await resetSession(session.value.sessionId)
    session.value = response.data
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    await loadDatasets()
  } catch (error) {
    notify('无法连接后端：' + error.message)
  }
})
</script>

<style scoped>
.lab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(31, 45, 61, 0.07);
}

.field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
}

.field select {
  padding: 6px 10px;
  font-size: 13px;
  color: #1f2937;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #fbfcfe;
}

.buttons {
  display: flex;
  gap: 8px;
}

.btn {
  padding: 7px 16px;
  font-size: 13px;
  color: #334155;
  background: #f1f5f9;
  border: none;
  border-radius: 9px;
  cursor: pointer;
}

.btn.primary {
  color: #fff;
  background: linear-gradient(120deg, #3b6cf6, #6f5cf0);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.stats {
  display: flex;
  gap: 16px;
  margin-left: auto;
  font-size: 12px;
  color: #64748b;
}

.stats strong {
  color: #1f2937;
}

.notice {
  margin: 0;
  padding: 10px 14px;
  font-size: 13px;
  color: #b91c1c;
  background: #fef2f2;
  border-radius: 10px;
}
</style>
