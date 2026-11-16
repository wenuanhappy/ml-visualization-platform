<template>
  <div class="lab">
    <section class="toolbar">
      <label class="field">
        <span>算法</span>
        <select v-model="algorithmId" @change="onAlgorithmChange">
          <option v-for="item in algorithms" :key="item.id" :value="item.id">{{ item.displayName }}</option>
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

    <div class="lab-body">
      <div class="lab-main">
        <VisualizationCanvas
      :dataset="dataset"
      :algorithm-type="algorithmType"
      :model="model"
      :lines="overlayLines"
      :projection-note="projectionNote"
          @axes-change="onAxesChange"
        />

        <EvaluationPanel :algorithm-type="algorithmType" :session="session" :dataset="dataset" />
      </div>

      <aside class="lab-side">
        <ParamPanel
          :algorithm="algorithm"
          :parameter-values="parameterValues"
          @parameter-change="onParameterChange"
          @reset-defaults="resetDefaults"
        />
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import VisualizationCanvas from '@/components/lab/VisualizationCanvas.vue'
import EvaluationPanel from '@/components/lab/EvaluationPanel.vue'
import ParamPanel from '@/components/lab/ParamPanel.vue'
import { createSession, getDataset, getDatasets, getProjection, resetSession, stepSession } from '@/api/ml'
import { ALGORITHMS, defaultParameterValues, toBackendParameters } from '@/services/algorithms'

const algorithms = ALGORITHMS

const algorithmId = ref('linear_regression')
const datasetId = ref(null)
const datasets = ref([])
const dataset = ref(null)
const session = ref(null)
const busy = ref(false)
const notice = ref('')
const axes = ref({ x: null, y: null })
const projection = ref(null)
const algorithm = computed(() => algorithms.find(item => item.id === algorithmId.value) || algorithms[0])
const parameterValues = ref(defaultParameterValues(algorithm.value))

const algorithmType = computed(() => algorithm.value.type ?? 'regression')
const model = computed(() => session.value?.modelData ?? {})
const currentStep = computed(() => session.value?.currentStep ?? 0)
const totalSteps = computed(() => session.value?.totalSteps ?? 0)
const loss = computed(() => session.value?.loss ?? null)
const score = computed(() => session.value?.accuracy ?? null)
const scoreLabel = computed(() => session.value?.scoreLabel ?? '得分')
const finished = computed(() => session.value?.status === 'finished')

const projectionNote = computed(() => {
  if (algorithmId.value === 'pca') {
    return 'PCA 视图：散点坐标为主成分投影，颜色保留原始类别。'
  }
  return projection.value?.note ?? ''
})

/** 画布上的线全部来自后端投影接口，前端不再自己算 */
const overlayLines = computed(() => projection.value?.lines ?? [])

function onAxesChange(next) {
  axes.value = next
  refreshProjection()
}

async function refreshProjection() {
  if (!session.value || !axes.value.x || !axes.value.y) {
    projection.value = null
    return
  }
  try {
    const response = await getProjection(session.value.sessionId, axes.value.x, axes.value.y)
    projection.value = response.data
  } catch (error) {
    projection.value = null
  }
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
  projection.value = null
  parameterValues.value = defaultParameterValues(algorithm.value)
}

function onParameterChange({ paramName, value }) {
  parameterValues.value = { ...parameterValues.value, [paramName]: value }
  // 参数变了，当前会话与投影作废，下次「开始」会用新参数重建
  session.value = null
  projection.value = null
}

function resetDefaults() {
  parameterValues.value = defaultParameterValues(algorithm.value)
  session.value = null
  projection.value = null
}

async function ensureSession() {
  if (session.value) return
  const response = await createSession({
    algorithm: algorithmId.value,
    datasetId: datasetId.value,
    parameters: toBackendParameters(algorithm.value, parameterValues.value)
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
      await refreshProjection()
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
    await refreshProjection()
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
    projection.value = null
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

.lab-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 288px;
  gap: 16px;
  align-items: start;
}

@media (max-width: 1180px) {
  .lab-body {
    grid-template-columns: 1fr;
  }
}

.lab-main,
.lab-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
