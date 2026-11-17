/**
 * 引导式实验案例。
 *
 * 逐条对照原项目 迁移前 Angular 版本的 learning-guide-data.service
 * 的 guidedCases 与 buildGuidanceState：案例标题、摘要、目标、验证文案、
 * 参数预设与阶段文案全部照抄。
 *
 * 数据集 / 算法名映射到本项目后端的标识（kmeans → k_means，svm → support_vector_machine）。
 */

export const GUIDED_CASES = [
  {
    id: 'case-kmeans-iris',
    title: '使用K-Means聚类Iris数据',
    summary: '观察K值如何影响iris花瓣特征的聚类紧凑性。',
    category: 'unsupervised',
    algorithmName: 'kmeans',
    algorithmId: 'k_means',
    datasetName: 'make_blobs',
    datasetCode: 'make_blobs',
    parameterPreset: { k: 3, maxIterations: 80 },
    objectives: [
      '加载K=3的聚类预设。',
      '运行训练并监控轮廓系数的改进。',
      '使用解释面板比较聚类中心分离说明。'
    ],
    validationText: '轮廓系数应稳定在0.70以上，聚类中心摘要应提到三个紧凑的组。'
  },
  {
    id: 'case-logistic-boundary',
    title: '分类合成数据点',
    summary: '训练逻辑回归并检查特征权重如何驱动决策边界。',
    category: 'supervised',
    algorithmName: 'logistic_regression',
    algorithmId: 'logistic_regression',
    datasetName: 'make_classification',
    datasetCode: 'make_classification',
    parameterPreset: { learningRate: 0.08, iterations: 120 },
    objectives: [
      '加载分类预设。',
      '观察训练过程中准确率、精确率、召回率和F1值的提升。',
      '验证权重图表中特征Y是最强的正向信号。'
    ],
    validationText: '准确率应超过90%，解释面板应显示特征Y具有最强的正向权重。'
  },
  {
    id: 'case-tree-depth',
    title: '解释决策树',
    summary: '检查最大深度如何改变iris数据上树的结构和纯度。',
    category: 'supervised',
    algorithmName: 'decision_tree',
    algorithmId: 'decision_tree',
    datasetName: 'iris',
    datasetCode: 'iris',
    parameterPreset: { maxDepth: 4, minSamplesSplit: 2, criterion: 'gini' },
    objectives: [
      '加载决策树预设。',
      '运行训练直到树解释出现。',
      '使用结构视图识别根节点分裂和叶节点纯度。'
    ],
    validationText: '树应渲染至少五个节点，根节点在花瓣长度上分裂，并有清晰的类别纯度说明。'
  },
  {
    id: 'case-linear-weights',
    title: '解释线性回归器',
    summary: '跟踪损失下降并解释住房数据的特征权重大小。',
    category: 'supervised',
    algorithmName: 'linear_regression',
    algorithmId: 'linear_regression',
    datasetName: 'boston_housing',
    datasetCode: 'boston_housing',
    parameterPreset: { learningRate: 0.03, iterations: 100 },
    objectives: [
      '加载回归预设。',
      '观察损失曲线在迭代过程中的下降。',
      '确认平均房间数仍是最强的正向系数。'
    ],
    validationText: '损失应稳定下降，解释面板应显示平均房间数作为首要正向驱动因素。'
  },
  {
    id: 'case-svm-margin',
    title: '比较边界分类器',
    summary: '在合成数据集上使用SVM并检查RBF核函数下的指标。',
    category: 'supervised',
    algorithmName: 'svm',
    algorithmId: 'support_vector_machine',
    datasetName: 'make_classification',
    datasetCode: 'make_classification',
    parameterPreset: { kernel: 'rbf', c: 1.2, iterations: 80 },
    objectives: [
      '加载SVM预设。',
      '运行训练以将其指标面板与逻辑回归进行比较。',
      '阅读关于边界稳定性和支持向量的摘要解释。'
    ],
    validationText: 'F1值应保持较高，解释面板应描述稳定的分离边界。'
  }
]

export const PHASE_LABELS = ['加载预设', '检查设置', '运行训练', '查看指标', '验证结果']

export function caseById(id) {
  return GUIDED_CASES.find(item => item.id === id) || null
}

/**
 * 对照原项目 buildGuidanceState。
 * @param {string|null} caseId
 * @param {{status:string,currentStep:number,totalSteps:number}} session
 */
export function buildGuidanceState(caseId, session) {
  if (!caseId) {
    return {
      caseId: null,
      phase: 'idle',
      activeStep: 0,
      headline: '加载预设案例以获取引导式工作流。'
    }
  }

  const currentStep = session?.currentStep ?? 0
  const totalSteps = session?.totalSteps ?? 0
  const progress = totalSteps ? currentStep / totalSteps : 0
  const status = session?.status ?? 'idle'

  if (status === 'idle' && currentStep === 0) {
    return {
      caseId,
      phase: 'configured',
      activeStep: 1,
      headline: '预设已加载。请检查推荐的算法、数据集和参数。'
    }
  }

  if (status === 'training' || status === 'paused' || status === 'ready') {
    if (currentStep === 0) {
      return {
        caseId,
        phase: 'configured',
        activeStep: 1,
        headline: '预设已加载。请检查推荐的算法、数据集和参数。'
      }
    }
    return {
      caseId,
      phase: 'running',
      activeStep: progress > 0.6 ? 3 : 2,
      headline: '训练正在进行。请同时查看指标面板和解释卡片。'
    }
  }

  return {
    caseId,
    phase: 'verified',
    activeStep: 4,
    headline: '验证通过。请查看解释视图并与案例目标进行比较。'
  }
}
