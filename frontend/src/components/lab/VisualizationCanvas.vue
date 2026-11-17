<template>
  <section class="canvas-card">
    <header class="canvas-header">
      <span class="title-text">可视化画布</span>

      <button v-if="pca3dToggleVisible" type="button" class="view-toggle" @click="togglePca3d">
        {{ pca3dActive ? '切回 2D' : '切换 3D' }}
      </button>

      <span v-if="dataset && !isPcaView" class="axis-pickers">
        <label class="axis-picker">
          <span class="axis-label">X 轴</span>
          <select :value="selectedXName" @change="onAxisChange('x', $event.target.value)">
            <option v-for="dim in axisOptions" :key="dim" :value="dim" :disabled="dim === selectedYName">
              {{ dim }}
            </option>
          </select>
        </label>
        <label class="axis-picker">
          <span class="axis-label">Y 轴</span>
          <select :value="selectedYName" @change="onAxisChange('y', $event.target.value)">
            <option v-for="dim in axisOptions" :key="dim" :value="dim" :disabled="dim === selectedXName">
              {{ dim }}
            </option>
          </select>
        </label>
      </span>
    </header>

    <div v-if="dataset" class="canvas-wrapper">
      <div v-if="projectionNote" class="axis-hint">
        <span class="axis-hint-icon">i</span>
        <span>{{ projectionNote }}</span>
      </div>

      <div v-if="isPcaView" class="axis-hint">
        <span class="axis-hint-icon">i</span>
        <span>已把数据投影到 {{ pcaDimensions }} 个主成分上，散点按原始类别着色。</span>
      </div>

      <div class="chart-area">
        <div v-if="!pca3dActive" ref="chartHost" class="chart-frame"></div>

        <div v-else class="pca3d-frame">
          <canvas
            ref="pca3dHost"
            class="pca3d-canvas"
            @pointerdown="onPca3dPointerDown"
            @pointermove="onPca3dPointerMove"
            @pointerup="onPca3dPointerUp"
            @pointerleave="onPca3dPointerUp"
          ></canvas>
          <div class="pca3d-hint">
            <span>按住拖拽可旋转视角</span>
            <button type="button" class="pca3d-reset" @click="resetPca3dView">重置视角</button>
          </div>
        </div>

        <div v-if="importances.length" ref="importanceHost" class="importance-frame"></div>
      </div>

      <div v-if="uniqueLabels.length" class="legend">
        <div class="legend-title">点图例</div>
        <div v-for="label in uniqueLabels" :key="label" class="legend-item">
          <span class="legend-color" :style="{ backgroundColor: legendColor(label) }"></span>
          <span class="legend-label">{{ label }}</span>
        </div>
      </div>
    </div>

    <div v-else class="empty-canvas">
      <div class="empty-icon">◌</div>
      <p class="empty-text">选择数据集以预览可视化区域。</p>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const init = echarts.init

const emit = defineEmits(['axes-change'])

const props = defineProps({
  dataset: { type: Object, default: null },
  algorithmType: { type: String, default: 'regression' },
  // 后端返回的模型：assignments / regressionLine / projections / centroids
  model: { type: Object, default: () => ({}) },
  // 训练后的分配结果（分类的预测类别 / 聚类的簇号），优先用于着色
  assignments: { type: Array, default: () => [] },
  // 质心在当前两个维度上的坐标（来自后端投影接口，kind=centroids）
  centroids: { type: Array, default: () => [] },
  // 画布要叠加的线段：[[x1,y1],[x2,y2]] 的数组（决策树可能多条）
  lines: { type: Array, default: () => [] },
  projectionNote: { type: String, default: '' }
})

const COLOR_MAP = {
  setosa: '#38BDF8',
  versicolor: '#FB923C',
  virginica: '#34D399',
  '0': '#38BDF8',
  '1': '#F472B6',
  '2': '#34D399',
  '3': '#FB923C',
  '簇 1': '#38BDF8',
  '簇 2': '#F472B6',
  '簇 3': '#34D399'
}

const chartHost = ref(null)
const pca3dHost = ref(null)
const importanceHost = ref(null)
let chart = null
let importanceChart = null

// PCA 3D 视图（对照原项目 renderPca3d / onPca3dPointer*）
const pca3dMode = ref(false)
const pca3dYaw = ref(-0.62)
const pca3dPitch = ref(0.38)
let pca3dDragging = false
let pca3dLast = { x: 0, y: 0 }

const pca3dAvailable = computed(() => (props.model?.projections?.[0]?.length ?? 0) >= 3)
/** 真正渲染 3D：状态位残留但数据不足 3 维时一律退回 2D（原项目 pca3dActive） */
const pca3dActive = computed(() => pca3dMode.value && pca3dAvailable.value)
/** 按钮可见：可切 3D，或正处于 3D 需要「切回 2D」 */
const pca3dToggleVisible = computed(() => pca3dAvailable.value || pca3dMode.value)

const importances = computed(() => props.model?.featureImportances ?? [])

const hasTarget = computed(() => (props.dataset?.points ?? []).some(point => point.target != null))

const axisOptions = computed(() => {
  const names = [...(props.dataset?.featureNames ?? [])]
  const targetName = props.dataset?.targetName
  // 只有回归类数据集（点里带 target）才把目标列作为可选维度；
  // 分类数据集的 targetName 只是标签列名，不能当坐标轴
  if (hasTarget.value && targetName && !names.includes(targetName)) {
    names.push(targetName)
  }
  return names
})
const selectedXName = ref(null)
const selectedYName = ref(null)

const isPcaView = computed(() => props.algorithmType === 'dimension')
const pcaDimensions = computed(() => props.model?.projections?.[0]?.length ?? 0)

const points = computed(() => props.dataset?.points ?? [])

const uniqueLabels = computed(() => {
  const labels = points.value.map((point, index) => resolvePointLabel(point, index))
  return [...new Set(labels)]
})

function resolvePointLabel(point, index) {
  const assigned = props.assignments?.[index]
  if (assigned != null) {
    const named = props.dataset?.classNames?.[assigned]
    return named ?? `簇 ${assigned + 1}`
  }
  if (point.label != null) {
    return props.dataset?.classNames?.[point.label] ?? String(point.label)
  }
  return '样本'
}

function legendColor(label) {
  if (COLOR_MAP[label]) {
    return COLOR_MAP[label]
  }
  const base = label.replace(/^预测\s*/, '')
  return COLOR_MAP[base] || '#378ADD'
}

function onAxisChange(axis, value) {
  if (axis === 'x') {
    selectedXName.value = value
  } else {
    selectedYName.value = value
  }
  emit('axes-change', { x: selectedXName.value, y: selectedYName.value })
}

function featureValue(point, name) {
  if (name && name === props.dataset?.targetName) {
    return point.target ?? 0
  }
  const index = (props.dataset?.featureNames ?? []).indexOf(name)
  return index >= 0 ? point.features[index] : 0
}

const pointCoords = computed(() =>
  points.value.map((point, index) => {
    if (isPcaView.value) {
      const projection = props.model?.projections?.[index]
      if (projection?.length) {
        // 只有 1 个主成分时，Y 只做固定幅度错位，避免所有点重叠
        const y = projection.length < 2 ? ((index % 9) - 4) * 0.05 : projection[1] ?? 0
        return { x: projection[0], y }
      }
      return { x: point.features[0] ?? 0, y: point.features[1] ?? 0 }
    }
    const x = featureValue(point, selectedXName.value)
    const y = featureValue(point, selectedYName.value)
    return { x, y }
  })
)

function groupPoints() {
  const grouped = {}
  pointCoords.value.forEach((coord, index) => {
    const label = resolvePointLabel(points.value[index], index)
    if (!grouped[label]) {
      grouped[label] = []
    }
    grouped[label].push([coord.x, coord.y])
  })
  return grouped
}

function buildScatterSeries() {
  const grouped = groupPoints()
  return Object.entries(grouped).map(([label, data]) => ({
    name: label,
    type: 'scatter',
    symbolSize: 7,
    data,
    itemStyle: {
      color: legendColor(label),
      borderColor: 'rgba(255,255,255,0.45)',
      borderWidth: 1,
      shadowBlur: 5,
      shadowColor: `${legendColor(label)}50`
    },
    emphasis: {
      itemStyle: {
        borderColor: '#ffffff',
        borderWidth: 1.5,
        shadowBlur: 12,
        shadowColor: `${legendColor(label)}80`
      }
    }
  }))
}

function buildOverlaySeries() {
  const isRegression = props.algorithmType === 'regression'
  const series = props.lines.map(line => ({
    name: isRegression ? '回归线' : '决策边界',
    type: 'line',
    data: line,
    symbol: 'none',
    lineStyle: isRegression
      ? { color: 'rgba(0, 0, 0, 0.65)', width: 1.5 }
      : { color: 'rgba(0, 0, 0, 0.55)', width: 1.5, type: 'dashed' },
    z: 10
  }))

  const centroids = props.centroids
  if (props.algorithmType === 'clustering' && centroids?.length) {
    series.push({
      name: '聚类中心',
      type: 'scatter',
      symbol: 'diamond',
      symbolSize: 14,
      data: centroids.map((centroid, index) => [centroid[0], centroid[1], index]),
      itemStyle: {
        color: 'rgba(255,255,255,0.92)',
        borderColor: 'rgba(15,23,42,0.35)',
        borderWidth: 1.5,
        shadowBlur: 10,
        shadowColor: 'rgba(0,0,0,0.25)'
      },
      label: {
        show: true,
        formatter: params => `C${Number(params.value[2]) + 1}`,
        color: '#94A3B8',
        fontWeight: 600,
        fontSize: 10,
        position: 'top',
        distance: 6
      },
      z: 20
    })
  }
  return series
}

function renderChart() {
  if (!chartHost.value || !props.dataset) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartHost.value, undefined, { renderer: 'canvas' })
  }

  const xAxisLabel = isPcaView.value ? 'PC1' : (selectedXName.value || 'Feature X')
  const yAxisLabel = isPcaView.value ? 'PC2' : (selectedYName.value || 'Feature Y')

  chart.setOption({
    animation: true,
    animationDuration: 650,
    animationDurationUpdate: 520,
    animationEasing: 'cubicOut',
    animationDelay: index => index * 18,
    tooltip: {
      trigger: 'item',
      backgroundColor: '#111827',
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: '#E2E8F0' },
      formatter: params => {
        if (Array.isArray(params.value)) {
          return `${params.seriesName}<br/>${xAxisLabel}: ${params.value[0]}<br/>${yAxisLabel}: ${params.value[1]}`
        }
        return params.seriesName
      }
    },
    grid: { left: 56, right: 24, top: 26, bottom: 52 },
    xAxis: {
      type: 'value',
      name: xAxisLabel,
      nameLocation: 'middle',
      nameGap: 34,
      nameTextStyle: { color: '#1e293b', fontWeight: 600, fontSize: 11 },
      axisLine: { lineStyle: { color: '#94A3B8' } },
      axisLabel: { color: '#334155', fontSize: 10, fontWeight: 500 },
      splitLine: { lineStyle: { color: 'rgba(15,23,42,0.06)' } }
    },
    yAxis: {
      type: 'value',
      name: yAxisLabel,
      nameLocation: 'middle',
      nameGap: 44,
      nameTextStyle: { color: '#1e293b', fontWeight: 600, fontSize: 11 },
      axisLine: { lineStyle: { color: '#94A3B8' } },
      axisLabel: { color: '#334155', fontSize: 10, fontWeight: 500 },
      splitLine: { lineStyle: { color: 'rgba(15,23,42,0.06)' } }
    },
    series: [...buildScatterSeries(), ...buildOverlaySeries()]
  }, true)

  setTimeout(() => chart?.resize(), 50)
}

function togglePca3d() {
  // 没有 3 维数据时只允许「切回 2D」，不允许进入空白的三维画布
  if (!pca3dAvailable.value && !pca3dMode.value) {
    return
  }
  pca3dMode.value = !pca3dMode.value
  if (pca3dActive.value) {
    nextTick(() => renderPca3d())
  } else {
    nextTick(() => renderChart())
  }
}

function resetPca3dView() {
  pca3dYaw.value = -0.62
  pca3dPitch.value = 0.38
  renderPca3d()
}

function onPca3dPointerDown(event) {
  pca3dDragging = true
  pca3dLast = { x: event.clientX, y: event.clientY }
}

function onPca3dPointerMove(event) {
  if (!pca3dDragging) return
  const dx = event.clientX - pca3dLast.x
  const dy = event.clientY - pca3dLast.y
  pca3dLast = { x: event.clientX, y: event.clientY }
  pca3dYaw.value += dx * 0.01
  pca3dPitch.value = Math.max(-1.2, Math.min(1.2, pca3dPitch.value + dy * 0.01))
  renderPca3d()
}

function onPca3dPointerUp() {
  pca3dDragging = false
}

/** 对照原项目 renderPca3d：归一化到 [-1,1] 后做偏航/俯仰投影，按深度排序绘制 */
function renderPca3d() {
  const canvas = pca3dHost.value
  const points = props.model?.projections
  if (!canvas || !points?.length || (points[0]?.length ?? 0) < 3) {
    return
  }

  const dpr = window.devicePixelRatio || 1
  const rect = canvas.getBoundingClientRect()
  const width = Math.max(320, Math.round(rect.width || canvas.clientWidth || 480))
  const height = Math.max(260, Math.round(rect.height || canvas.clientHeight || 420))
  canvas.width = Math.round(width * dpr)
  canvas.height = Math.round(height * dpr)

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)

  const bounds = [0, 1, 2].map(axis => {
    const values = points.map(point => point[axis] ?? 0)
    const min = Math.min(...values)
    const max = Math.max(...values)
    const span = max - min || 1
    return { min, span, mid: (min + max) / 2 }
  })
  const normalized = points.map(point =>
    [0, 1, 2].map(axis => ((point[axis] ?? 0) - bounds[axis].mid) / (bounds[axis].span / 2))
  )

  const cosYaw = Math.cos(pca3dYaw.value)
  const sinYaw = Math.sin(pca3dYaw.value)
  const cosPitch = Math.cos(pca3dPitch.value)
  const sinPitch = Math.sin(pca3dPitch.value)
  const centerX = width / 2
  const centerY = height / 2
  const radius = Math.min(width, height) * 0.34
  const focal = 3.4

  const project = p => {
    const ry = p[0] * cosYaw + p[2] * sinYaw
    const rz = -p[0] * sinYaw + p[2] * cosYaw
    const py = p[1] * cosPitch - rz * sinPitch
    const pz = p[1] * sinPitch + rz * cosPitch
    const scale = focal / (focal + pz)
    return { x: centerX + ry * radius * scale, y: centerY - py * radius * scale, depth: pz }
  }

  const origin = project([0, 0, 0])
  ctx.lineWidth = 1
  ;[['PC1', [1.15, 0, 0]], ['PC2', [0, 1.15, 0]], ['PC3', [0, 0, 1.15]]].forEach(([label, end]) => {
    const tip = project(end)
    ctx.strokeStyle = 'rgba(100,116,139,0.45)'
    ctx.beginPath()
    ctx.moveTo(origin.x, origin.y)
    ctx.lineTo(tip.x, tip.y)
    ctx.stroke()
    ctx.fillStyle = '#475569'
    ctx.font = '600 11px "Segoe UI", system-ui, sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(label, tip.x, tip.y - 10)
  })

  normalized
    .map((point, index) => ({ ...project(point), index }))
    .sort((a, b) => b.depth - a.depth)
    .forEach(point => {
      const raw = points[point.index]
      const label = resolvePointLabel(props.dataset?.points?.[point.index], point.index)
      const depthScale = 0.78 + (1 - Math.min(1, Math.max(0, (point.depth + 1) / 2))) * 0.5
      ctx.beginPath()
      ctx.arc(point.x, point.y, 3.6 * depthScale, 0, Math.PI * 2)
      ctx.fillStyle = legendColor(label)
      ctx.globalAlpha = 0.92
      ctx.fill()
      ctx.globalAlpha = 1
      ctx.lineWidth = 0.8
      ctx.strokeStyle = 'rgba(255,255,255,0.65)'
      ctx.stroke()
    })
}

/** 特征重要性小图（对照原项目 importance-frame） */
function renderImportance() {
  if (!importanceHost.value || !importances.value.length) {
    return
  }
  if (!importanceChart) {
    importanceChart = init(importanceHost.value)
  }
  const top = [...importances.value].slice(0, 6).reverse()
  importanceChart.setOption({
    grid: { left: 78, right: 16, top: 8, bottom: 8 },
    tooltip: { trigger: 'axis', backgroundColor: '#111827', textStyle: { color: '#E2E8F0' } },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category',
      data: top.map(item => item.feature),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#475569', fontSize: 10 }
    },
    series: [{
      type: 'bar',
      data: top.map(item => item.importance),
      barMaxWidth: 10,
      itemStyle: { color: '#38BDF8', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 9, color: '#64748b', formatter: '{c}' }
    }]
  }, true)
}

function resize() {
  chart?.resize()
  importanceChart?.resize()
  if (pca3dActive.value) renderPca3d()
}

onMounted(() => {
  window.addEventListener('resize', resize)
  resetAxes()
  renderChart()
  nextTick(() => renderImportance())
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

function resetAxes() {
  const names = axisOptions.value
  selectedXName.value = names[0] ?? null
  const preferredY = hasTarget.value && props.dataset?.targetName
    ? props.dataset.targetName
    : (names[1] ?? names[0] ?? null)
  selectedYName.value = preferredY
  emit('axes-change', { x: selectedXName.value, y: selectedYName.value })
}

// 对照原项目 ngOnChanges：换数据集 / 换算法 / 拿到新的训练结果都退回二维视图
watch(() => props.dataset, () => {
  pca3dMode.value = false
})

watch(() => props.algorithmType, () => {
  pca3dMode.value = false
})

watch(() => props.model, (next, previous) => {
  if (next !== previous) {
    // 每次新的训练结果（含改主成分数后重训）都回到二维，
    // 否则新结果只有 1~2 个主成分时会停在 3D 且看不到「切回 2D」
    pca3dMode.value = false
  }
})

watch(
  () => props.dataset,
  () => {
    resetAxes()
    renderChart()
  }
)

watch(
  () => [props.model, props.lines, props.assignments, importances.value, selectedXName.value, selectedYName.value, pca3dActive.value],
  renderChart,
  { deep: true }
)
</script>

<style scoped>
.canvas-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px 20px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(31, 45, 61, 0.07);
}

.canvas-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.title-text {
  font-size: 15px;
  font-weight: 600;
}

.axis-pickers {
  display: flex;
  gap: 10px;
}

.axis-picker {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #fbfcfe;
}

.axis-label {
  font-size: 11px;
  color: #94a3b8;
}

.axis-picker select {
  border: none;
  background: transparent;
  font-size: 12px;
  color: #1e293b;
  font-weight: 600;
  outline: none;
}

.chart-frame {
  width: 100%;
  height: 460px;
}

.view-toggle {
  margin-left: auto;
  margin-right: 12px;
  padding: 4px 10px;
  font-size: 11px;
  color: #1d4ed8;
  background: #eef4ff;
  border: 1px solid rgba(29, 78, 216, 0.25);
  border-radius: 8px;
  cursor: pointer;
}

.pca3d-frame {
  position: relative;
}

.pca3d-canvas {
  width: 100%;
  height: 460px;
  cursor: grab;
}

.pca3d-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #94a3b8;
}

.pca3d-reset {
  font-size: 11px;
  color: #1d4ed8;
  background: none;
  border: none;
  cursor: pointer;
}

.importance-frame {
  width: 100%;
  height: 150px;
  margin-top: 8px;
}

.axis-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 12px;
  color: #475569;
  background: #f4f7fd;
  border-radius: 10px;
}

.axis-hint-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: #94a3b8;
  border-radius: 50%;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding-top: 6px;
  font-size: 12px;
  color: #475569;
}

.legend-title {
  font-weight: 600;
  color: #334155;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-color {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.empty-canvas {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 320px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 34px;
}
</style>
