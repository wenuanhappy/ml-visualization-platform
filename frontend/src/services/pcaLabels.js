/**
 * 根据 PCA 载荷矩阵为每个主成分自动生成「含义标签」。
 *
 * 逐行对照原项目 迁移前 Angular 版本的 pca-labels：
 *  - 主导特征：|载荷| 从大到小取前 3 个，且 |载荷| >= 0.35；
 *  - 若主导特征名有公共词前缀（按 _ / 空格 / - 切词），用它做概念名；
 *  - 所有主导特征同号 → 「正向综合轴」；正负混合 → 「对比轴」；单个 → 「主导轴」。
 */

const DOMINANT_THRESHOLD = 0.35
const DOMINANT_MAX = 3

function splitWords(name) {
  return String(name ?? '')
    .toLowerCase()
    .split(/[_\s-]+/)
    .filter(word => word.length > 0)
}

function commonWordPrefix(names) {
  if (names.length < 2) {
    return ''
  }
  let prefixWords = splitWords(names[0])
  if (!prefixWords.length) {
    return ''
  }
  for (let i = 1; i < names.length; i++) {
    const words = splitWords(names[i])
    const keep = []
    for (let j = 0; j < Math.min(prefixWords.length, words.length); j++) {
      if (prefixWords[j] === words[j]) {
        keep.push(prefixWords[j])
      } else {
        break
      }
    }
    prefixWords = keep
    if (!prefixWords.length) {
      return ''
    }
  }
  return prefixWords.join(' ')
}

/**
 * @param {{ pcaComponents?: number[][], featureNames?: string[], explainedVariance?: number[] }} model
 */
export function describePcaComponents(model) {
  const components = model?.pcaComponents
  const names = model?.featureNames ?? []
  if (!components?.length || !names.length) {
    return []
  }
  const ratios = model?.explainedVariance ?? []

  return components.map((vector, index) => {
    const ranked = vector
      .map((loading, featureIndex) => ({
        name: names[featureIndex] ?? `dim${featureIndex + 1}`,
        loading: loading ?? 0
      }))
      .sort((a, b) => Math.abs(b.loading) - Math.abs(a.loading))
      .filter(item => Math.abs(item.loading) >= DOMINANT_THRESHOLD)
      .slice(0, DOMINANT_MAX)

    // 特征特别多时阈值可能把前 3 全过滤掉，兜底保留第一名
    if (!ranked.length && vector.length) {
      const top = vector
        .map((loading, featureIndex) => ({
          name: names[featureIndex] ?? `dim${featureIndex + 1}`,
          loading: loading ?? 0
        }))
        .sort((a, b) => Math.abs(b.loading) - Math.abs(a.loading))[0]
      ranked.push(top)
    }

    const dominantNames = ranked.map(item => item.name)
    const concept = commonWordPrefix(dominantNames) || dominantNames[0] || `PC${index + 1}`
    const sameSign = ranked.length > 0 && ranked.every(item => Math.sign(item.loading) === Math.sign(ranked[0].loading))
    const kindLabel = ranked.length <= 1 ? '主导轴' : sameSign ? '正向综合轴' : '对比轴'

    const featureList = ranked
      .map(item => `${item.name}(${item.loading >= 0 ? '+' : ''}${item.loading.toFixed(2)})`)
      .join('、')
    const relation = ranked.length <= 1 ? '贡献最大' : sameSign ? '方向一致' : '方向相反'
    const description = ranked.length
      ? `主要由 ${featureList} 构成，${relation}，可看作「${concept}」相关的${kindLabel}`
      : '各原始维度贡献都较小，未找到主导特征'

    return {
      index,
      ratio: ratios.length > index ? ratios[index] : null,
      shortLabel: `${concept}·${kindLabel}`,
      description,
      dominant: ranked
    }
  })
}
