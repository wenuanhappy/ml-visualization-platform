<template>
  <section class="feature-card">
    <header class="panel-header">
      <h3>特征选择</h3>
    </header>

    <label class="field">
      <span>数据集</span>
      <select v-model.number="datasetId" @change="onDatasetChange">
        <option v-for="item in datasets" :key="item.id" :value="item.id">{{ item.name }}</option>
      </select>
    </label>

    <label class="field">
      <span>算法</span>
      <select v-model="algorithmId">
        <option v-for="item in algorithms" :key="item.id" :value="item.id">{{ item.displayName }}</option>
      </select>
    </label>

    <div v-if="featureNames.length" class="feature-list">
      <label v-for="name in featureNames" :key="name" class="feature-item">
        <input type="checkbox" :value="name" v-model="selectedFeatures" />
        <span>{{ name }}</span>
      </label>
    </div>
    <p v-else class="empty-text">选择数据集后可以勾选参与训练的特征。</p>

    <button class="btn primary" :disabled="!selectedFeatures.length || loading" @click="train">
      {{ loading ? '训练中…' : `用 ${selectedFeatures.length} 个特征训练` }}
    </button>

    <div v-if="result" class="result-block">
      <div class="result-row"><span>{{ result.scoreLabel }}</span><strong>{{ Number(result.accuracy ?? 0).toFixed(4) }}</strong></div>
      <div class="result-row"><span>损失</span><strong>{{ Number(result.loss ?? 0).toFixed(4) }}</strong></div>
      <div class="result-row"><span>使用特征</span><strong>{{ result.modelData?.featureNames?.length ?? 0 }} 个</strong></div>
      <p class="result-note">{{ result.modelData?.featureNames?.join('、') }}</p>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { featureSelectionTrain, getDataset, getDatasets } from '@/api/ml'
import { ALGORITHMS, algorithmById, defaultParameterValues, toBackendParameters } from '@/services/algorithms'

const algorithms = ALGORITHMS
const datasets = ref([])
const datasetId = ref(null)
const algorithmId = ref('linear_regression')
const featureNames = ref([])
const selectedFeatures = ref([])
const result = ref(null)
const loading = ref(false)

async function onDatasetChange() {
  const response = await getDataset(datasetId.value)
  featureNames.value = response.data?.featureNames ?? []
  selectedFeatures.value = [...featureNames.value]
  result.value = null
}

async function train() {
  loading.value = true
  try {
    const algorithm = algorithmById(algorithmId.value)
    const response = await featureSelectionTrain({
      datasetId: datasetId.value,
      algorithm: algorithmId.value,
      features: selectedFeatures.value,
      parameters: toBackendParameters(algorithm, defaultParameterValues(algorithm))
    })
    result.value = response.data
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const response = await getDatasets()
  datasets.value = response.data ?? []
  datasetId.value = datasets.value[0]?.id ?? null
  if (datasetId.value) {
    await onDatasetChange()
  }
})
</script>

<style scoped>
.feature-card {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
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

.field select {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
  padding: 5px 8px;
  font-size: 11px;
  color: #0f172a;
  background: #fbfcfe;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.feature-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  max-height: 140px;
  overflow-y: auto;
}

.feature-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #475569;
}

.feature-item input {
  accent-color: #378add;
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

.empty-text {
  margin: 0;
  font-size: 11px;
  color: #94a3b8;
}

.result-block {
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 10px;
}

.result-row {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}

.result-row strong {
  color: #0f172a;
}

.result-note {
  margin: 6px 0 0;
  font-size: 10px;
  line-height: 1.6;
  color: #94a3b8;
}
</style>
