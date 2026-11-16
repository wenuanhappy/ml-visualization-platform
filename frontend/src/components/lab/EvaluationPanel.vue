<template>
  <div class="eval-wrap">
    <p class="section-label">
      {{ state.title }}
      <span v-if="state.pca" class="pca-badge">{{ pcaComponentCount }} 个主成分</span>
    </p>

    <!-- PCA：只展示主成分分析 -->
    <template v-if="state.pca">
      <div v-if="explained.length" class="pca-section-label">方差解释率</div>
      <div v-if="explained.length" class="pca-variance">
        <div v-for="(ratio, index) in explained" :key="index" class="pca-variance-row">
          <span class="pca-pc">PC{{ index + 1 }}</span>
          <div class="pca-bar">
            <span class="pca-bar-fill" :style="{ width: `${ratio * 100}%` }"></span>
          </div>
          <span class="pca-value">{{ (ratio * 100).toFixed(1) }}%</span>
        </div>
      </div>

      <div v-if="loadings.length" class="pca-section-label">载荷矩阵</div>
      <div v-if="loadings.length" class="pca-table-wrap">
        <table class="pca-loadings">
          <thead>
            <tr>
              <th>原始维度</th>
              <th v-for="(ratio, index) in explained" :key="index">PC{{ index + 1 }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in loadings" :key="row.name">
              <td>{{ row.name }}</td>
              <td
                v-for="(value, index) in row.values"
                :key="index"
                :class="{ 'pca-strong': value >= 0.5 || value <= -0.5 }"
              >
                {{ value.toFixed(3) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="explained.length" class="pca-total">
        累计解释率
        <strong>{{ (cumulative * 100).toFixed(1) }}%</strong>
      </div>

      <p class="pca-hint">
        载荷绝对值越大，该维度对这个主成分的贡献越大。主成分数设为 1 时沿 PC1 单轴展示，设为 3 时可在画布右上角切换到 3D 视图；换数据集或重新训练会自动回到 2D。
      </p>
    </template>

    <!-- 普通评估：指标卡 + 训练轨迹 -->
    <template v-else>
      <div v-if="cardViews.length" class="metric-grid">
        <div v-for="card in cardViews" :key="card.label" class="metric-card" :style="{ '--accent': card.color }">
          <div class="card-label">{{ card.label }}</div>
          <div class="card-value">{{ card.value }}</div>
          <div v-if="card.delta" class="card-delta" :class="card.positive ? 'up' : 'down'">
            {{ card.delta }}
          </div>
        </div>
      </div>

      <div v-if="chartPoints.length" class="chart-card">
        <div class="chart-header">
          <div>
            <span class="chart-title">训练轨迹</span>
            <span class="chart-sub">
              {{ normalizeLabel(state.lossLabel) }} ↓ · {{ normalizeLabel(state.scoreLabel) }} ↑
            </span>
          </div>
          <div class="legend">
            <span class="leg-item"><span class="leg-line loss"></span>{{ normalizeLabel(state.lossLabel) }}</span>
            <span class="leg-item"><span class="leg-line score"></span>{{ normalizeLabel(state.scoreLabel) }}</span>
          </div>
        </div>

        <div ref="chartHost" class="chart-area"></div>
      </div>

      <p v-if="!cardViews.length" class="empty-copy">实验开始后将显示指标和训练曲线。</p>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { buildEvaluationState, normalizeLabel, viewModelOf } from '@/services/evaluation'

const props = defineProps({
  algorithmType: { type: String, default: 'regression' },
  session: { type: Object, default: null },
  dataset: { type: Object, default: null }
})

const chartHost = ref(null)
let chart = null

const state = computed(() => buildEvaluationState({
  algorithmType: props.algorithmType,
  session: props.session
}))

const cardViews = computed(() => viewModelOf(state.value.cards ?? []))
const chartPoints = computed(() => state.value.chart ?? [])

const explained = computed(() => props.session?.modelData?.explainedVariance ?? [])
const cumulative = computed(() => explained.value.reduce((sum, ratio) => sum + ratio, 0))
const pcaComponentCount = computed(() => explained.value.length)

const loadings = computed(() => {
  const components = props.session?.modelData?.pcaComponents ?? []
  const names = props.dataset?.featureNames ?? []
  if (!components.length || !names.length) {
    return []
  }
  // 主成分矩阵是「每个主成分在各维度上的系数」，转成「维度 × 主成分」的行
  return names.map((name, featureIndex) => ({
    name,
    values: components.map(component => component[featureIndex] ?? 0)
  }))
})

function renderChart() {
  if (state.value.pca) {
    return
  }
  if (!chartHost.value || !chartPoints.value.length) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartHost.value, undefined, { renderer: 'canvas' })
  }

  const lossLabel = normalizeLabel(state.value.lossLabel)
  const scoreLabel = normalizeLabel(state.value.scoreLabel)
  const totalSteps = state.value.totalSteps

  chart.setOption({
    animation: true,
    animationDuration: 500,
    animationDurationUpdate: 220,
    animationEasing: 'cubicOut',
    animationEasingUpdate: 'cubicOut',
    grid: { left: 48, right: 42, top: 18, bottom: 34 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#111827',
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: '#E2E8F0' },
      formatter: params => {
        const items = Array.isArray(params) ? params : [params]
        const step = items[0]?.value?.[0] ?? ''
        const rows = items.map(item => `${item.marker}${item.seriesName}: ${item.value?.[1]}`)
        return `步骤 ${step}<br/>${rows.join('<br/>')}`
      }
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: totalSteps && totalSteps > 0 ? totalSteps : undefined,
      minInterval: 1,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } },
      axisLabel: { color: '#94A3B8', fontSize: 10, formatter: value => `${Math.round(value)}` },
      splitLine: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        name: lossLabel,
        nameTextStyle: { color: '#64748B', fontSize: 10, padding: [0, 0, 8, 0] },
        axisLabel: { color: '#94A3B8', fontSize: 10 },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } }
      },
      {
        type: 'value',
        name: scoreLabel,
        nameTextStyle: { color: '#64748B', fontSize: 10, padding: [0, 0, 8, 0] },
        axisLabel: { color: '#94A3B8', fontSize: 10, formatter: value => `${Math.round(value * 100)}%` },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: lossLabel,
        type: 'line',
        yAxisIndex: 0,
        smooth: 0.4,
        symbol: 'circle',
        symbolSize: 5,
        showSymbol: false,
        lineStyle: { width: 1.5, color: '#38BDF8' },
        itemStyle: {
          color: '#38BDF8',
          borderColor: 'rgba(56,189,248,0.35)',
          borderWidth: 4
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(56,189,248,0.13)' },
            { offset: 0.7, color: 'rgba(56,189,248,0.03)' },
            { offset: 1, color: 'rgba(56,189,248,0)' }
          ])
        },
        data: chartPoints.value.map(point => [point.step, point.loss])
      },
      {
        name: scoreLabel,
        type: 'line',
        yAxisIndex: 1,
        smooth: 0.4,
        symbol: 'circle',
        symbolSize: 5,
        showSymbol: false,
        lineStyle: { width: 1.5, color: '#C084FC' },
        itemStyle: {
          color: '#C084FC',
          borderColor: 'rgba(192,132,252,0.35)',
          borderWidth: 4
        },
        data: chartPoints.value.map(point => [point.step, point.score])
      }
    ]
  }, true)

  setTimeout(() => chart?.resize(), 50)
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', resize)
  renderChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

watch(() => [props.session, props.algorithmType], renderChart, { deep: true })
</script>

<style scoped>
.eval-wrap {
  background: #ffffff;
  border: 1px solid rgba(226, 232, 240, 0.7);
  border-radius: 18px;
  padding: 20px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.14);
}

.section-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
  margin: 0 0 14px;
}

.pca-badge {
  margin-left: 8px;
  padding: 2px 8px;
  font-size: 10px;
  color: #378add;
  background: rgba(55, 138, 221, 0.1);
  border-radius: 999px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  padding: 14px 16px;
  background: rgba(6, 182, 212, 0.06);
  border: 1px solid rgba(226, 232, 240, 0.5);
  border-radius: 12px;
}

.metric-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--accent);
}

.card-label {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.card-value {
  font-size: 22px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1;
}

.card-delta {
  font-size: 10px;
  margin-top: 8px;
  font-weight: 600;
}

.card-delta.up {
  color: #10b981;
}

.card-delta.down {
  color: #ef4444;
}

.chart-card {
  padding: 16px 18px;
  background: rgba(6, 182, 212, 0.04);
  border: 1px solid rgba(226, 232, 240, 0.5);
  border-radius: 12px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 14px;
}

.chart-title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.chart-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #64748b;
}

.legend {
  display: flex;
  gap: 14px;
}

.leg-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #64748b;
}

.leg-line {
  width: 18px;
  height: 2px;
  border-radius: 2px;
  display: inline-block;
}

.leg-line.loss {
  background: #378add;
}

.leg-line.score {
  background: #a78bfa;
}

.chart-area {
  height: 180px;
}

.empty-copy {
  margin: 0;
  color: #94a3b8;
}

.pca-section-label {
  font-size: 11px;
  font-weight: 600;
  color: #475569;
  margin: 14px 0 8px;
}

.pca-variance-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 12px;
}

.pca-pc {
  width: 32px;
  color: #64748b;
  font-weight: 600;
}

.pca-bar {
  flex: 1;
  height: 7px;
  background: rgba(148, 163, 184, 0.18);
  border-radius: 999px;
  overflow: hidden;
}

.pca-bar-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #378add, #38bdf8);
  border-radius: 999px;
}

.pca-value {
  width: 52px;
  text-align: right;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.pca-table-wrap {
  overflow-x: auto;
}

.pca-loadings {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

.pca-loadings th,
.pca-loadings td {
  padding: 6px 8px;
  text-align: right;
  border-bottom: 1px solid rgba(226, 232, 240, 0.7);
  color: #475569;
}

.pca-loadings th:first-child,
.pca-loadings td:first-child {
  text-align: left;
  color: #334155;
}

.pca-strong {
  color: #0f172a;
  font-weight: 700;
}

.pca-total {
  margin-top: 12px;
  font-size: 12px;
  color: #64748b;
}

.pca-total strong {
  margin-left: 6px;
  color: #0f172a;
  font-size: 15px;
}

.pca-hint {
  margin: 12px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: #94a3b8;
}
</style>
