<template>
  <section class="comparison-card">
    <header class="panel-header">
      <h3>模型对比</h3>
      <div class="metric-toggle">
        <button :class="{ active: metric === 'accuracy' }" @click="metric = 'accuracy'">得分</button>
        <button :class="{ active: metric === 'loss' }" @click="metric = 'loss'">损失</button>
      </div>
    </header>

    <label class="field">
      <span>选择数据集</span>
      <select v-model.number="datasetId">
        <option v-for="item in datasets" :key="item.id" :value="item.id">{{ item.name }}</option>
      </select>
    </label>

    <div class="add-row">
      <select v-model="pendingAlgorithm">
        <option v-for="item in algorithms" :key="item.id" :value="item.id">{{ item.displayName }}</option>
      </select>
      <button class="btn" @click="addConfig">+ 添加配置</button>
    </div>

    <div v-if="configs.length" class="config-list">
      <div v-for="(config, index) in configs" :key="config.key" class="config-card">
        <div class="config-head">
          <span>#{{ index + 1 }} {{ algorithmById(config.algorithmId).displayName }}</span>
          <button class="link-danger" @click="configs.splice(index, 1)">删除</button>
        </div>
        <input v-model="config.alias" class="alias-input" placeholder="别名（用于图表显示）" />
        <div class="param-row">
          <label v-for="param in algorithmById(config.algorithmId).parameters" :key="param.name">
            <span>{{ param.displayName }}</span>
            <input
              v-if="param.type === 'number'"
              type="number"
              :min="param.min"
              :max="param.max"
              :step="param.step"
              v-model.number="config.parameters[param.name]"
            />
            <select v-else v-model="config.parameters[param.name]">
              <option v-for="option in param.options" :key="option" :value="option">{{ option }}</option>
            </select>
          </label>
        </div>
      </div>
    </div>

    <button class="btn primary" :disabled="!configs.length || !datasetId || loading" @click="runComparison">
      {{ loading ? '计算中…' : '开始对比' }}
    </button>

    <div v-if="results.length" class="result-block">
      <div ref="chartHost" class="chart"></div>
      <ul class="result-list">
        <li v-for="item in results" :key="item.alias">
          <span>{{ item.alias }}</span>
          <strong>{{ metric === 'accuracy' ? Number(item.accuracy ?? 0).toFixed(4) : Number(item.loss ?? 0).toFixed(4) }}</strong>
          <small>{{ item.scoreLabel }}</small>
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { compareModels, getDatasets } from '@/api/ml'
import { ALGORITHMS, algorithmById, defaultParameterValues, toBackendParameters } from '@/services/algorithms'

const datasets = ref([])
const datasetId = ref(null)
const algorithms = ALGORITHMS
const pendingAlgorithm = ref(ALGORITHMS[0].id)
const configs = ref([])
const results = ref([])
const metric = ref('accuracy')
const loading = ref(false)
const chartHost = ref(null)
let chart = null

let seq = 0

function addConfig() {
  const algorithm = algorithmById(pendingAlgorithm.value)
  configs.value.push({
    key: ++seq,
    algorithmId: algorithm.id,
    alias: algorithm.displayName,
    parameters: defaultParameterValues(algorithm)
  })
}

async function runComparison() {
  loading.value = true
  try {
    const response = await compareModels({
      datasetId: datasetId.value,
      configs: configs.value.map(config => ({
        algorithm: config.algorithmId,
        alias: config.alias || algorithmById(config.algorithmId).displayName,
        parameters: toBackendParameters(algorithmById(config.algorithmId), config.parameters)
      }))
    })
    results.value = response.data ?? []
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartHost.value || !results.value.length) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartHost.value, undefined, { renderer: 'canvas' })
  }
  const values = results.value.map(item => Number(metric.value === 'accuracy' ? item.accuracy ?? 0 : item.loss ?? 0))
  chart.setOption({
    grid: { left: 44, right: 18, top: 18, bottom: 42 },
    tooltip: { trigger: 'axis', backgroundColor: '#111827', textStyle: { color: '#E2E8F0' } },
    xAxis: {
      type: 'category',
      data: results.value.map(item => item.alias),
      axisLabel: { color: '#64748b', fontSize: 10 },
      axisLine: { lineStyle: { color: '#cbd5e1' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94a3b8', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(15,23,42,0.06)' } }
    },
    series: [{
      type: 'bar',
      data: values,
      barMaxWidth: 42,
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#38BDF8' },
          { offset: 1, color: '#378ADD' }
        ])
      }
    }]
  }, true)
}

watch(metric, () => renderChart())

onMounted(async () => {
  const response = await getDatasets()
  datasets.value = response.data ?? []
  datasetId.value = datasets.value[0]?.id ?? null
})

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.comparison-card {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.metric-toggle {
  display: flex;
  gap: 2px;
  padding: 2px;
  background: #f1f5f9;
  border-radius: 8px;
}

.metric-toggle button {
  padding: 4px 10px;
  font-size: 10px;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.metric-toggle button.active {
  color: #1d4ed8;
  background: #fff;
  font-weight: 600;
}

.field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
}

.field > span {
  flex: none;
}

.field select,
.add-row select,
.alias-input,
.param-row input,
.param-row select {
  min-width: 0;
  max-width: 100%;
  padding: 5px 8px;
  font-size: 11px;
  color: #0f172a;
  background: #fbfcfe;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.add-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.add-row select {
  flex: 1 1 130px;
  min-width: 0;
}

.add-row .btn {
  flex: 1 1 auto;
  white-space: nowrap;
}

.btn {
  padding: 6px 12px;
  font-size: 11px;
  color: #334155;
  background: #f1f5f9;
  border: none;
  border-radius: 8px;
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

.config-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}

.config-card {
  padding: 10px;
  background: #f8fafc;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.config-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #475569;
}

.link-danger {
  font-size: 10px;
  color: #b91c1c;
  background: none;
  border: none;
  cursor: pointer;
}

.alias-input {
  width: 100%;
}

.param-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.param-row label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: #94a3b8;
}

.param-row input {
  width: 62px;
}

.chart {
  width: 100%;
  height: 180px;
}

.result-list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.result-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #475569;
}

.result-list strong {
  color: #0f172a;
}

.result-list small {
  margin-left: auto;
  color: #94a3b8;
}
</style>
