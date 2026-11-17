/**
 * 模型解释的数据构造。
 *
 * 逐条对照原项目 迁移前 Angular 版本的 learning-guide-data.service：
 * buildDecisionTreeExplanation / buildWeightExplanation / buildKMeansExplanation
 * 以及 buildSimulation 里按算法分派 explanation 的分支。
 *
 * 注意：原项目的解释面板本身用的是**静态示意数据**（权重条、树节点都不是真实模型输出），
 * 这里保持同样行为，避免「迁移后行为变了」。
 */

const TREE_EXPLANATION = maxDepth => ({
  mode: 'tree',
  title: '决策树结构',
  description: `深度${maxDepth}允许树将iris特征空间分裂成越来越纯的叶节点。`,
  notes: [
    '根节点分裂强调花瓣长度，因为它能早期分离setosa。',
    '左分支快速变得纯净，而右分支仍需区分versicolor和virginica。',
    '随着训练进行，后续叶节点细化类别纯度。'
  ],
  treeNodes: [
    { id: 'root', x: 170, y: 26, label: '花瓣长度 < 2.4', detail: '150样本 · gini 0.66', tone: 'cyan' },
    { id: 'left', x: 62, y: 132, label: 'setosa', detail: '50样本 · 纯度 1.00', tone: 'green' },
    { id: 'right', x: 278, y: 132, label: '花瓣宽度 < 1.8', detail: '100样本 · gini 0.49', tone: 'purple' },
    { id: 'mid-left', x: 220, y: 238, label: 'versicolor', detail: '47样本 · 纯度 0.91', tone: 'green' },
    { id: 'mid-right', x: 336, y: 238, label: 'virginica', detail: '53样本 · 纯度 0.94', tone: 'cyan' }
  ],
  treeEdges: [
    { from: 'root', to: 'left' },
    { from: 'root', to: 'right' },
    { from: 'right', to: 'mid-left' },
    { from: 'right', to: 'mid-right' }
  ]
})

function buildWeightExplanation(modelName, progress) {
  const scale = 0.4 + progress * 0.6
  const weights = modelName === 'linear_regression'
    ? [
        { feature: '平均房间数', value: 0.92 * scale },
        { feature: 'LSTAT', value: -0.61 * scale },
        { feature: 'PTRATIO', value: -0.35 * scale },
        { feature: 'NOX', value: -0.21 * scale }
      ]
    : [
        { feature: '特征Y', value: 0.88 * scale },
        { feature: '特征X', value: 0.42 * scale },
        { feature: '偏置', value: -0.28 * scale }
      ]

  return {
    mode: 'weights',
    title: modelName === 'linear_regression' ? '线性特征权重' : '逻辑特征权重',
    description: '正权重将预测向上或向类别1推进，负权重则向相反方向拉动。',
    notes: [
      '权重条按绝对贡献排序，主导因素排在顶部。',
      modelName === 'linear_regression'
        ? '平均房间数对更高的房价预测有正向贡献。'
        : '特征Y主导向正类别的向上移动。'
    ],
    weights
  }
}

function buildKMeansExplanation(progress, k) {
  return {
    mode: 'summary',
    title: '聚类中心行为摘要',
    description: `K-Means当前在特征空间中追踪${k}个聚类中心作为聚类锚点。`,
    notes: [
      '聚类中心开始时分散，随着迭代增加变得更加紧凑。',
      '轮廓系数衡量当前聚类的分离程度。',
      progress >= 1 ? '聚类中心已稳定，可比较K值选择并报告最佳分组。' : '观察聚类中心稳定过程中聚类分数的提升。'
    ]
  }
}

const SVM_SUMMARY = {
  mode: 'summary',
  title: '支持向量摘要',
  description: 'SVM强调边界稳定性而非直接可解释的特征权重。',
  notes: [
    '当前边界以高置信度分离两个合成类别。',
    '正则化参数C控制边界的软硬程度。',
    '使用指标面板将SVM与逻辑回归进行比较。'
  ]
}

const EMPTY = {
  mode: 'summary',
  title: '模型解释',
  description: '选择算法以解锁特征或结构解释。',
  notes: ['决策树渲染节点图。', '线性模型渲染权重条。', 'K-Means显示聚类中心行为说明。']
}

/**
 * @param {object} options
 * @param {string} options.algorithmName 后端算法标识（linear_regression / decision_tree / …）
 * @param {number} options.progress 0~1
 * @param {object} options.parameters 当前超参数
 */
export function buildExplanation({ algorithmName, progress = 0, parameters = {} }) {
  if (!algorithmName) {
    return EMPTY
  }
  if (algorithmName === 'decision_tree') {
    const explanation = TREE_EXPLANATION(Number(parameters.maxDepth || 4))
    explanation.notes = [
      ...explanation.notes.slice(0, 2),
      progress >= 1 ? '树生长完成；每个节点内有悬停等效的详细信息。' : explanation.notes[2]
    ]
    return explanation
  }
  if (algorithmName === 'linear_regression' || algorithmName === 'logistic_regression') {
    return buildWeightExplanation(algorithmName, progress)
  }
  if (algorithmName === 'k_means') {
    return buildKMeansExplanation(progress, Number(parameters.k || 3))
  }
  return SVM_SUMMARY
}
