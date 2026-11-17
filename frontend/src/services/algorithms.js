/**
 * 算法与超参数定义。
 *
 * 逐条对照原项目 迁移前 Angular 版本的 learning-guide-data.service
 * 的 mockAlgorithms（当后端 /api/algorithms 不可用时的兜底定义），
 * 参数名、显示名、默认值、范围、步长、下拉选项都保持一致。
 *
 * 与后端参数的对应关系写在 backendKey 上：
 * 原项目用 iterations / maxIterations，本项目后端用 steps 表示迭代次数。
 */

const TIPS = {
  learningRate: '每一步沿梯度方向移动的幅度。太大容易震荡，太小收敛很慢。',
  iterations: '一共执行多少步梯度下降。步数越多拟合越充分，但耗时更长。',
  maxDepth: '树的最大深度。越深越容易过拟合，越浅越容易欠拟合。',
  minSamplesSplit: '一个节点至少有多少样本才允许继续分裂，值越大树越保守。',
  criterion: '分裂时的不纯度度量：gini 为基尼不纯度，entropy 为信息熵。',
  k: '要把样本聚成几类。K 过大可能把真实的一簇拆开。',
  maxIterations: 'K-Means 最多迭代多少轮，轮数足够时质心会稳定下来。',
  kernel: 'linear 用直线分界；rbf 用高斯核；poly 用二次多项式核，边界更弯曲。',
  c: '正则化参数。越大越不允许违反间隔，边界更紧贴样本，容易过拟合。',
  gamma: '核宽度。越大每个样本影响范围越小，边界越弯曲。',
  trees: '森林里树的数量。树越多越稳定，但训练更慢。',
  n_components: '保留几个主成分。通常取 2 个就能画出二维投影。'
}

function numberParam(name, displayName, def, min, max, step, backendKey) {
  return {
    name,
    displayName,
    type: 'number',
    default: def,
    min,
    max,
    step,
    backendKey: backendKey || name,
    tip: TIPS[name] || ''
  }
}

export const ALGORITHMS = [
  {
    id: 'linear_regression',
    name: 'linear_regression',
    displayName: '线性回归',
    description: '使用可解释的特征权重拟合连续目标值。',
    type: 'regression',
    parameters: [
      numberParam('learningRate', '学习率', 0.03, 0.001, 0.2, 0.001),
      numberParam('iterations', '迭代次数', 100, 20, 200, 10, 'steps')
    ]
  },
  {
    id: 'logistic_regression',
    name: 'logistic_regression',
    displayName: '逻辑回归',
    description: '具有线性决策边界和特征权重的二分类算法。',
    type: 'classification',
    parameters: [
      numberParam('learningRate', '学习率', 0.08, 0.001, 0.2, 0.001),
      numberParam('iterations', '迭代次数', 120, 20, 240, 10, 'steps')
    ]
  },
  {
    id: 'decision_tree',
    name: 'decision_tree',
    displayName: '决策树',
    description: '贪心特征分裂，可直接可视化为可解释的树结构。',
    type: 'classification',
    parameters: [
      numberParam('maxDepth', '最大深度', 4, 1, 8, 1),
      numberParam('minSamplesSplit', '最小分裂样本数', 2, 2, 10, 1),
      { name: 'criterion', displayName: '分裂准则', type: 'select', default: 'gini', options: ['gini', 'entropy'], tip: TIPS.criterion }
    ]
  },
  {
    id: 'k_means',
    name: 'kmeans',
    displayName: 'K-Means聚类',
    description: '围绕聚类中心对样本进行聚类，比较不同K值的效果。',
    type: 'clustering',
    parameters: [
      numberParam('k', '聚类数量', 3, 2, 6, 1),
      numberParam('maxIterations', '最大迭代次数', 80, 20, 200, 10)
    ]
  },
  {
    id: 'support_vector_machine',
    name: 'svm',
    displayName: '支持向量机',
    description: '基于边界的分类器，可调节正则化参数。',
    type: 'classification',
    parameters: [
      { name: 'kernel', displayName: '核函数', type: 'select', default: 'rbf', options: ['linear', 'rbf', 'poly'], tip: TIPS.kernel },
      numberParam('c', '正则化参数C', 1, 0.1, 4, 0.1),
      numberParam('gamma', '核宽度 γ', 0.5, 0.05, 2, 0.05),
      numberParam('iterations', '迭代次数', 80, 20, 200, 10, 'steps')
    ]
  },
  {
    id: 'random_forest',
    name: 'random_forest',
    displayName: '随机森林',
    description: '多棵决策树投票，树的数量与单树深度可调。',
    type: 'classification',
    parameters: [
      numberParam('trees', '树的数量', 8, 4, 20, 1, 'treeCount'),
      numberParam('maxDepth', '单树最大深度', 3, 1, 8, 1)
    ]
  },
  {
    id: 'pca',
    name: 'pca',
    displayName: '主成分分析',
    description: '把原始维度线性组合成互不相关的主成分。',
    type: 'dimension',
    parameters: [numberParam('n_components', '主成分数量', 2, 1, 3, 1, 'nComponents')]
  }
]

export function algorithmById(id) {
  return ALGORITHMS.find(item => item.id === id) || ALGORITHMS[0]
}

export function defaultParameterValues(algorithm) {
  const values = {}
  for (const param of algorithm.parameters) {
    values[param.name] = param.default
  }
  return values
}

/** UI 参数名 → 后端参数名 */
export function toBackendParameters(algorithm, values) {
  const payload = {}
  for (const param of algorithm.parameters) {
    const key = param.backendKey || param.name
    payload[key] = values[param.name]
  }
  return payload
}

export function formatParameterValue(param, value) {
  if (param.type !== 'number') {
    return String(value)
  }
  if (param.step && param.step < 1) {
    return Number(value).toFixed(param.step.toString().split('.')[1]?.length || 2)
  }
  return String(value)
}
