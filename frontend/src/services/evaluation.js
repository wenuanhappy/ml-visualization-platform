/**
 * 评估面板的数据推导，逐行对照原项目
 * 迁移前 Angular 版本的 learning-guide-data.service 的
 * buildEvaluation / buildMetricCards / normalizeLabel / formatCardValue。
 *
 * 差异：原项目的 chart 优先用后端累积的真实 metrics，这里同样只用真实 metrics，
 * 不再保留「演示模式」的合成曲线（本项目的后端始终可用）。
 */

const TONES = {
  cyan: '#378ADD',
  purple: '#A78BFA',
  green: '#10B981',
  amber: '#F59E0B'
}

export function normalizeLabel(label) {
  const trimmed = (label ?? '').trim()
  return /^R.{0,2}$/.test(trimmed) ? 'R²' : trimmed
}

function toneColor(tone) {
  return TONES[tone] || '#378ADD'
}

function formatCardValue(card) {
  if (card.value === undefined || card.value === null || Number.isNaN(card.value)) {
    return ''
  }
  const mode = card.display || (card.suffix === '%' ? (card.value <= 1.2 ? 'ratioPercent' : 'percent') : 'number')
  if (mode === 'ratioPercent') {
    return `${(card.value * 100).toFixed(1)}${card.suffix || ''}`
  }
  if (mode === 'percent') {
    return `${card.value.toFixed(0)}${card.suffix || ''}`
  }
  return `${card.value.toFixed(card.value >= 10 ? 1 : 2)}${card.suffix || ''}`
}

function deltaText(card) {
  if (card.delta === undefined || card.delta === null || Number.isNaN(card.delta)) {
    return ''
  }
  const sign = card.delta > 0 ? '+' : ''
  const decimals = Math.abs(card.delta) < 0.01 ? 4 : 2
  return `${sign}${card.delta.toFixed(decimals)} 较上一步`
}

function isPositive(card) {
  if (card.delta === undefined || card.delta === null) {
    return true
  }
  return card.lowerIsBetter ? card.delta <= 0 : card.delta >= 0
}

function labelsFor(algorithmType, model) {
  const fallbackScore = algorithmType === 'clustering'
    ? 'Silhouette'
    : algorithmType === 'regression' ? 'R2' : 'Accuracy'
  const fallbackLoss = algorithmType === 'clustering'
    ? 'Inertia'
    : algorithmType === 'regression' ? 'MSE' : 'Loss'
  return {
    scoreLabel: model?.scoreLabel || fallbackScore,
    lossLabel: fallbackLoss
  }
}

function buildMetricCards(algorithmType, session, chart, labels, started) {
  const last = chart[chart.length - 1]
  const previous = chart[chart.length - 2]
  const deltaOf = pick =>
    started && last && previous ? Number((pick(last) - pick(previous)).toFixed(4)) : undefined

  const scoreIcon = algorithmType === 'clustering'
    ? 'bubble_chart'
    : algorithmType === 'regression' ? 'show_chart' : 'track_changes'

  return [
    {
      label: labels.scoreLabel,
      value: started ? session?.accuracy : undefined,
      suffix: '%',
      display: 'ratioPercent',
      delta: deltaOf(point => point.score),
      lowerIsBetter: false,
      tone: 'cyan',
      icon: scoreIcon
    },
    {
      label: labels.lossLabel,
      value: started ? session?.loss : undefined,
      display: 'number',
      delta: deltaOf(point => point.loss),
      lowerIsBetter: true,
      tone: 'purple',
      icon: 'timeline'
    }
  ]
}

/**
 * @param {object} options
 * @param {'classification'|'regression'|'clustering'|'dimension'} options.algorithmType
 * @param {object|null} options.session 后端会话响应
 */
export function buildEvaluationState({ algorithmType, session }) {
  if (algorithmType === 'dimension') {
    return {
      title: '主成分分析',
      subtitle: '计算完成后显示方差解释率、主成分含义与载荷矩阵。',
      scoreLabel: 'ExplainedVar',
      lossLabel: '',
      cards: [],
      chart: [],
      totalSteps: 0,
      pca: true
    }
  }

  if (!session) {
    // 原项目在开始训练前也渲染指标卡与轨迹组件，只是数值为空、曲线无点
    const labels = labelsFor(algorithmType, null)
    const titles = {
      regression: '回归质量',
      clustering: '聚类指标',
      classification: '评估指标'
    }
    return {
      title: titles[algorithmType] ?? titles.classification,
      subtitle: '实验开始后将显示指标和训练曲线。',
      scoreLabel: labels.scoreLabel,
      lossLabel: labels.lossLabel,
      cards: buildMetricCards(algorithmType, null, [], labels, false),
      chart: [],
      totalSteps: 100
    }
  }

  const model = session.modelData ?? {}
  const totalSteps = session.totalSteps || 0
  const started = session.currentStep > 0 || Boolean(session.sessionId)
  const chart = (session.metrics ?? []).map(point => ({
    step: point.step,
    loss: point.loss,
    score: point.score
  }))
  const labels = labelsFor(algorithmType, model)

  const titles = {
    regression: '回归质量',
    clustering: '聚类指标',
    classification: '评估指标'
  }
  const subtitles = {
    regression: '跟踪优化过程中的拟合质量和误差收缩。',
    clustering: '检查聚类的紧凑性与分离度。',
    classification: '边界质量和类别置信度'
  }

  return {
    title: titles[algorithmType] ?? titles.classification,
    subtitle: subtitles[algorithmType] ?? subtitles.classification,
    scoreLabel: labels.scoreLabel,
    lossLabel: labels.lossLabel,
    cards: buildMetricCards(algorithmType, session, chart, labels, started),
    chart,
    totalSteps
  }
}

export function viewModelOf(cards) {
  return cards.map(card => ({
    label: normalizeLabel(card.label),
    value: formatCardValue(card),
    color: toneColor(card.tone),
    icon: card.icon,
    delta: deltaText(card),
    positive: isPositive(card)
  }))
}
