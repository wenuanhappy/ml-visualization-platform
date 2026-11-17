<template>
  <div class="experiment-shell">
    <section class="hero-strip">
      <div class="hero-main">
        <div class="hero-kicker">学习指导系统</div>
        <h2>模型解释、指标仪表板和引导式实验案例 -<br />整合在一个工作流程中。</h2>
        <p>{{ statusText }}</p>
      </div>
      <div class="hero-status">
        <div class="status-pill">{{ statusLabel }}</div>
        <div class="status-metric">
          <span>步数</span>
          <strong>{{ currentStep }} / {{ totalSteps || '—' }}</strong>
        </div>
        <div class="status-metric">
          <span>损失</span>
          <strong>{{ formatNumber(loss) }}</strong>
        </div>
      </div>
    </section>

    <p v-if="notice" class="notice">{{ notice }}</p>

    <div class="experiment-layout">
      <div class="left-panel">
        <AlgorithmSelector :algorithms="algorithms" :selected-id="algorithmId" @select="chooseAlgorithm" />
        <DatasetSelector :datasets="datasets" :selected-id="datasetId" @select="chooseDataset" @uploaded="loadDatasets" />
        <ComparisonPanel />
        <FeatureSelectionPanel />
      </div>

      <div class="center-panel">
        <VisualizationCanvas
          :dataset="dataset"
          :algorithm-type="algorithmType"
          :model="model"
          :lines="overlayLines"
          :projection-note="projectionNote"
          :assignments="chartAssignments"
          :centroids="chartCentroids"
          @axes-change="onAxesChange"
        />

        <EvaluationPanel :algorithm-type="algorithmType" :session="session" :dataset="dataset" />
      </div>

      <div class="right-panel">
        <ParamPanel
          :algorithm="algorithm"
          :parameter-values="parameterValues"
          @parameter-change="onParameterChange"
          @reset-defaults="resetDefaults"
        />

        <section class="control-card">
          <header class="card-head"><h3>训练控制</h3></header>
          <div class="control-body">
            <div class="control-buttons">
              <button class="btn primary" :disabled="!dataset || busy || running" @click="startTraining">
                <span class="icon">▶</span> 开始
              </button>
              <button class="btn" :disabled="!running" @click="pauseTraining">
                <span class="icon">⏸</span> 暂停
              </button>
              <button class="btn" :disabled="running || !session || finished" @click="resumeTraining">
                <span class="icon">▶</span> 继续
              </button>
              <button class="btn danger" :disabled="!session" @click="resetTraining">
                <span class="icon">↺</span> 重置
              </button>
              <button class="btn" :disabled="!dataset || busy || running || finished" @click="singleStep">
                <span class="icon">⏭</span> 单步执行
              </button>
            </div>

            <div class="progress-section">
              <p>当前步数：{{ currentStep }} / {{ totalSteps || '—' }}</p>
              <p>准确率 / 得分：{{ scorePercent }}</p>
            </div>
          </div>
        </section>

        <TrainingLog :entries="logEntries" :status="logStatus" />

        <ModelExplanation :algorithm="algorithm" :explanation="explanation" />
      </div>
    </div>

    <CaseLibrary :active-case-id="activeCaseId" :guidance="guidance" @load-case="loadCase" />

    <AiAssistant :context="assistantContext" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import VisualizationCanvas from '@/components/lab/VisualizationCanvas.vue'
import EvaluationPanel from '@/components/lab/EvaluationPanel.vue'
import ParamPanel from '@/components/lab/ParamPanel.vue'
import TrainingLog from '@/components/lab/TrainingLog.vue'
import ModelExplanation from '@/components/lab/ModelExplanation.vue'
import CaseLibrary from '@/components/lab/CaseLibrary.vue'
import ComparisonPanel from '@/components/lab/ComparisonPanel.vue'
import FeatureSelectionPanel from '@/components/lab/FeatureSelectionPanel.vue'
import AlgorithmSelector from '@/components/lab/AlgorithmSelector.vue'
import DatasetSelector from '@/components/lab/DatasetSelector.vue'
import AiAssistant from '@/components/lab/AiAssistant.vue'
import { createSession, getDataset, getDatasets, getProjection, resetSession, stepSession } from '@/api/ml'
import { ALGORITHMS, defaultParameterValues, toBackendParameters } from '@/services/algorithms'
import { buildExplanation } from '@/services/explanations'
import { buildGuidanceState, caseById } from '@/services/cases'

const algorithms = ALGORITHMS

const algorithmId = ref('linear_regression')
const datasetId = ref(null)
const datasets = ref([])
const dataset = ref(null)
const session = ref(null)
const busy = ref(false)
const running = ref(false)
const notice = ref('')
const axes = ref({ x: null, y: null })
const projection = ref(null)
const algorithm = computed(() => algorithms.find(item => item.id === algorithmId.value) || algorithms[0])
const parameterValues = ref(defaultParameterValues(algorithm.value))
const logEntries = ref([])
const activeCaseId = ref('')

let timer = null

const algorithmType = computed(() => algorithm.value.type ?? 'regression')
const model = computed(() => session.value?.modelData ?? {})
const currentStep = computed(() => session.value?.currentStep ?? 0)
const totalSteps = computed(() => session.value?.totalSteps ?? 0)
const loss = computed(() => session.value?.loss ?? null)
const score = computed(() => session.value?.accuracy ?? null)
const finished = computed(() => session.value?.status === 'finished')
const logStatus = computed(() => (finished.value ? 'completed' : session.value?.status ?? 'idle'))

const progressRatio = computed(() => (totalSteps.value ? currentStep.value / totalSteps.value : 0))

/** 状态标签：对照原项目 trainingState.status 的展示 */
const statusLabel = computed(() => {
  if (finished.value) return 'Completed'
  if (running.value) return 'Training'
  if (currentStep.value > 0) return 'Paused'
  return 'Idle'
})

/** 对照原项目 buildSimulation 里按算法给出的 statusText */
const statusText = computed(() => {
  const done = progressRatio.value >= 1
  switch (algorithmId.value) {
    case 'decision_tree':
      return done ? '树结构已验证，可以查看解释。' : '树正在逐层分裂生长。'
    case 'linear_regression':
    case 'logistic_regression':
      return done ? '权重已稳定，可以进行解释。' : '权重正在每次优化步骤中更新。'
    case 'k_means':
      return done ? '聚类中心已稳定。' : '聚类中心仍在向密集区域移动。'
    case 'pca':
      return '点击开始训练，计算主成分并显示解释方差。'
    case 'random_forest':
      return done ? '森林已稳定，指标可供比较。' : '森林正在逐棵生长。'
    default:
      return done ? '边界已稳定，指标可供比较。' : '支持向量仍在识别中。'
  }
})

const scorePercent = computed(() => (score.value == null ? '—' : `${(Number(score.value) * 100).toFixed(1)}%`))

const assistantContext = computed(() =>
  [algorithm.value.displayName, dataset.value?.name].filter(Boolean).join(' / ')
)

const guidance = computed(() => buildGuidanceState(
  activeCaseId.value || null,
  session.value ?? { status: 'idle', currentStep: 0, totalSteps: 0 }
))

const explanation = computed(() => buildExplanation({
  algorithmName: session.value ? algorithmId.value : '',
  progress: progressRatio.value,
  parameters: parameterValues.value
}))

const projectionNote = computed(() => {
  // PCA 的二维画布现在画的是原始维度，后端那句「不再按原始维度画线」的提示会误导
  if (algorithmId.value === 'pca') {
    return ''
  }
  return projection.value?.note ?? ''
})

/** 画布上的线全部来自后端投影接口，前端不再自己算 */
const overlayLines = computed(() => projection.value?.lines ?? [])

/** 训练后的分配结果：分类为预测类别，聚类为簇号 */
const chartAssignments = computed(() => session.value?.modelData?.assignments ?? [])

/** 聚类中心必须用后端投影到当前两维的结果，不能直接取全维坐标的前两位 */
const chartCentroids = computed(() =>
  projection.value?.kind === 'centroids' ? projection.value.centroids ?? [] : []
)

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

function onAxesChange(next) {
  axes.value = next
  refreshProjection()
}

async function refreshProjection() {
  const isPcaAxis = name => typeof name === 'string' && name.startsWith('PC')
  if (!session.value || !axes.value.x || !axes.value.y
      || isPcaAxis(axes.value.x) || isPcaAxis(axes.value.y)) {
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
  stopTimer()
  session.value = null
  projection.value = null
  resetTrainingLog()
}

function chooseAlgorithm(id) {
  if (id === algorithmId.value) return
  algorithmId.value = id
  activeCaseId.value = ''
  stopTimer()
  session.value = null
  projection.value = null
  parameterValues.value = defaultParameterValues(algorithm.value)
  resetTrainingLog()
}

async function chooseDataset(id) {
  if (id === datasetId.value) return
  activeCaseId.value = ''
  datasetId.value = id
  await loadDataset(id)
}

async function loadCase(caseId) {
  const item = caseById(caseId)
  if (!item) return
  activeCaseId.value = caseId
  algorithmId.value = item.algorithmId
  const values = defaultParameterValues(algorithm.value)
  for (const [key, value] of Object.entries(item.parameterPreset)) {
    if (key in values) values[key] = value
  }
  parameterValues.value = values

  const target = datasets.value.find(entry => entry.code === item.datasetCode)
  if (target) {
    datasetId.value = target.id
    await loadDataset(target.id)
  }
  stopTimer()
  session.value = null
  projection.value = null
  resetTrainingLog()
}

function resetTrainingLog() {
  logEntries.value = algorithm.value && dataset.value
    ? [{ step: 0, text: `Preset ready: ${algorithm.value.displayName} on ${dataset.value.name}.`, tone: 'neutral' }]
    : []
}

function appendTrainingLog(text, tone, step) {
  logEntries.value = [...logEntries.value, { step, text, tone }].slice(-8)
}

/** 对照原项目 appendProgressLog：第 1 步、进度 35%、进度 70% 各打一条 */
function appendProgressLog(progress, step) {
  if (step <= 0) return
  const has = keyword => logEntries.value.some(entry => entry.text.includes(keyword))

  if (step === 1) {
    appendTrainingLog('First update received. Loss starts moving from the baseline.', 'neutral', step)
    return
  }
  if (progress >= 1) return
  if (progress >= 0.7 && !has('stabilizing')) {
    appendTrainingLog('Boundary and metrics are stabilizing. The model is close to convergence.', 'success', step)
    return
  }
  if (progress >= 0.35 && !has('clearer')) {
    appendTrainingLog('Loss is dropping and the score trend is getting clearer.', 'success', step)
  }
}

function onParameterChange({ paramName, value }) {
  parameterValues.value = { ...parameterValues.value, [paramName]: value }
  stopTimer()
  session.value = null
  projection.value = null
}

function resetDefaults() {
  parameterValues.value = defaultParameterValues(algorithm.value)
  stopTimer()
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
  resetTrainingLog()
}

async function doStep() {
  if (!session.value || finished.value) return
  busy.value = true
  try {
    const response = await stepSession(session.value.sessionId)
    session.value = response.data
    appendProgressLog(
      session.value.totalSteps ? session.value.currentStep / session.value.totalSteps : 0,
      session.value.currentStep
    )
    await refreshProjection()
    if (finished.value) stopTimer()
  } catch (error) {
    notify('训练失败：' + error.message)
    stopTimer()
  } finally {
    busy.value = false
  }
}

function scheduleStep(delay = 60) {
  clearTimeout(timer)
  timer = setTimeout(async () => {
    if (!running.value) return
    await doStep()
    if (running.value && !finished.value) scheduleStep()
  }, delay)
}

function stopTimer() {
  running.value = false
  clearTimeout(timer)
  timer = null
}

async function startTraining() {
  try {
    // 已经跑完还点「开始」：按原项目语义是重新训练一轮，
    // 否则 PCA 这类一次性算法看起来像"点了没反应"
    if (session.value && finished.value) {
      await resetTraining()
    }
    await ensureSession()
    if (finished.value) return
    running.value = true
    scheduleStep(0)
  } catch (error) {
    notify('创建训练会话失败：' + error.message)
  }
}

function pauseTraining() {
  stopTimer()
  appendTrainingLog('已暂停，可点「继续」恢复。', 'neutral', currentStep.value)
}

async function resumeTraining() {
  if (!session.value) {
    await startTraining()
    return
  }
  if (finished.value) return
  running.value = true
  scheduleStep(0)
}

async function singleStep() {
  await ensureSession()
  await doStep()
}

async function resetTraining() {
  stopTimer()
  if (!session.value) return
  busy.value = true
  try {
    const response = await resetSession(session.value.sessionId)
    session.value = response.data
    projection.value = null
    resetTrainingLog()
  } catch (error) {
    notify('重置失败：' + error.message)
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

onBeforeUnmount(stopTimer)
</script>

<style scoped>
.experiment-shell {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 24px clamp(18px, 4vw, 36px) 44px;
  /* 容器查询：嵌进主平台的「交互模拟」区域后，可用宽度由理论面板决定，
     而不是浏览器视口宽度，所以用 container query 而不是 media query */
  container-type: inline-size;
}

.hero-strip {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 30px 32px 26px;
  border-radius: 34px;
  background: linear-gradient(135deg, rgba(166, 237, 250, 0.18), rgba(139, 92, 246, 0.12));
  border: 1px solid rgba(75, 96, 122, 0.25);
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
}

.hero-kicker {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.18em;
  color: #0891b2;
}

.hero-main h2 {
  margin: 10px 0 8px;
  font-size: clamp(1.5rem, 2.4vw, 2.4rem);
  line-height: 1.12;
  letter-spacing: -0.04em;
  color: #0f172a;
  max-width: 820px;
}

.hero-main p {
  margin: 0;
  font-size: 13px;
  color: #475569;
  max-width: 720px;
}

.hero-status {
  display: grid;
  gap: 12px;
  align-content: start;
  min-width: 220px;
}

.status-pill,
.status-metric {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(6, 182, 212, 0.06);
  border: 1px solid rgba(226, 232, 240, 0.7);
}

.status-pill {
  font-weight: 700;
  text-align: center;
  background: linear-gradient(135deg, rgba(105, 222, 243, 0.18), rgba(139, 92, 246, 0.12));
  color: #0f172a;
}

.status-metric span {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: #64748b;
}

.status-metric strong {
  font-size: 1.3rem;
  color: #0f172a;
}

.experiment-layout {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr) 360px;
  gap: 20px;
}

@container (max-width: 1360px) {
  .experiment-layout {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .right-panel {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 18px;
  }
}

@container (max-width: 880px) {
  .experiment-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .left-panel {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  }
}

.left-panel,
.center-panel,
.right-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.control-card {
  border-radius: 24px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.14);
  overflow: hidden;
}

.card-head {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.5);
}

.card-head h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.control-body {
  padding: 16px 18px 18px;
}

.control-buttons {
  display: grid;
  gap: 10px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 11px 14px;
  font-size: 13px;
  color: #334155;
  background: #f1f5f9;
  border: 1px solid transparent;
  border-radius: 14px;
  cursor: pointer;
  transition: all 160ms ease;
}

.btn:hover:not(:disabled) {
  background: #e2e8f0;
}

.btn.primary {
  color: #fff;
  background: linear-gradient(135deg, #0891b2, #7c3aed);
}

.btn.danger {
  color: #b91c1c;
  background: #fef2f2;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.icon {
  font-size: 11px;
}

.progress-section {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.1), rgba(139, 92, 246, 0.08));
  color: #334155;
}

.progress-section p {
  margin: 0 0 6px;
  font-size: 12px;
}

.progress-section p:last-child {
  margin-bottom: 0;
}

.notice {
  margin: 0;
  padding: 10px 16px;
  font-size: 13px;
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 12px;
}
</style>
