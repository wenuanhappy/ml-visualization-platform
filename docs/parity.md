# 迁移对照表（Angular `master` → Vue）

> 对照基准：迁移前仓库 `源代码/` 下的 Angular 前端与 Spring 后端。
> 该目录已在迁移完成后删除（可从 git 历史取回），下表路径仅作历史对照。
> 状态：✅ 已迁移 / 🟡 已迁移但有已知差异 / ⏭ 按约定跳过

## 前端组件

| 原文件 | Vue 对应 | 状态 |
|---|---|---|
| `components/algorithm-selector` | `views/lab/ExperimentView.vue` 左栏 / 顶部算法下拉 | ✅ |
| `components/dataset-selector` | `components/lab/DatasetSelector.vue`（含 CSV 上传） | ✅ |
| `components/visualization-canvas`（929 行） | `components/lab/VisualizationCanvas.vue` | ✅ 含 PCA 3D 拖拽视图、单主成分错位、特征重要性小图 |
| `components/evaluation-panel` | `components/lab/EvaluationPanel.vue` + `services/evaluation.js` | ✅ 含 PCA 方差条与载荷矩阵 |
| `components/param-panel` | `components/lab/ParamPanel.vue` + `services/algorithms.js` | ✅ |
| `components/training-log` | `components/lab/TrainingLog.vue` | ✅ |
| `components/model-explanation` | `components/lab/ModelExplanation.vue` + `services/explanations.js` | 🟡 原项目本就是静态示意数据，已保持一致 |
| `components/case-library` | `components/lab/CaseLibrary.vue` + `services/cases.js` | ✅ 五个案例 |
| `components/model-comparison` | `components/lab/ComparisonPanel.vue` | ✅ 对应后端 `/api/ml/comparison` |
| `components/feature-selection` | `components/lab/FeatureSelectionPanel.vue` | ✅ 对应后端 `/api/ml/feature-selection` |
| `components/ai-assistant` | `components/lab/AiAssistant.vue` + `POST /api/ml/assistant` | ✅ 需要 `LLM_API_KEY`，未配置时返回提示 |
| `experiment/experiment.component` | `views/lab/ExperimentView.vue` | 🟡 训练循环用前端定时驱动，暂停/继续为前端语义 |
| `learning/learning.component` | `views/lab/LearningView.vue` + `services/lessons.js` + `services/questionBank.js` | ✅ 8 节课 / 200 题 / 错题集 |
| `services/api.service.ts` | `api/ml.js`（走 `@/utils/request`） | ✅ |
| `services/learning-guide-data.service.ts` | `services/{algorithms,cases,evaluation,explanations}.js` | 🟡 只迁移了真实指标链路，未搬 mock 兜底数据 |
| `services/experiment-context.service.ts` | — | ⏭ 单页应用不需要 |
| `utils/pca-labels.ts` | `services/pcaLabels.js` + 评估面板「主成分含义」 | ✅ |

## 后端

| 原文件 | Vue 版后端 | 状态 |
|---|---|---|
| `TrainingSimulationService`（3146 行） | `MlSimulationEngine`（线性回归/决策树）+ `MlAdvancedEngine`（逻辑回归/SVM/KMeans/PCA/随机森林） | ✅ 七算法 + 投影 |
| `project*` 投影（原 185-440 行） | `MlTrainingServiceImpl.projection` + `GET /api/ml/sessions/{id}/projection` | ✅ |
| `DatasetSeedInitializer` | `MlDatasetSeeder` | ✅ iris(4维)/boston(13维)/make_classification/make_blobs，另加一维回归教学集 |
| `AiAssistantService` | — | ⏭ 按约定跳过 |

## 已知差异（说明，不影响使用）

1. 数据集上传：原项目只写入元数据、不解析 CSV；本项目实现了真实的 CSV 解析
   （数值列作特征，最后一列非数值则作为类别、否则作为回归目标），属于增强。
2. 训练暂停/继续是前端定时器语义，不是服务端会话状态。
3. 模型解释的树/权重是原项目自带的静态示意数据（原项目即如此）。
4. 学习中心的「已完成课程」不持久化，只有错题集写 localStorage（与原项目一致）。
5. 前端仍是 JavaScript，未引入 TypeScript 类型层。
