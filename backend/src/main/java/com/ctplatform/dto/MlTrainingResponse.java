package com.ctplatform.dto;

import lombok.Data;

import java.util.List;

/**
 * 训练会话响应（第 08 组新增）。
 *
 * <p>字段与原项目的 {@code TrainingSimulationResponse} 保持同名，
 * 便于后续把 Angular 版的前端交互逻辑直接对应过来。</p>
 */
@Data
public class MlTrainingResponse {

    private Long sessionId;

    private String algorithm;

    private Long datasetId;

    private String datasetName;

    /** ready / training / finished */
    private String status;

    private Integer currentStep;

    private Integer totalSteps;

    /** 当前损失：回归为 MSE，决策树为平均叶节点不纯度 */
    private Double loss;

    /** 当前得分：回归为 R²，决策树为准确率 */
    private Double accuracy;

    /** 得分的含义，前端直接显示，避免硬编码 */
    private String scoreLabel;

    private List<MetricPoint> metrics;

    private ModelData modelData;

    @Data
    public static class MetricPoint {
        private Integer step;
        private Double loss;
        private Double score;
    }

    @Data
    public static class ModelData {
        /** 特征名 */
        private List<String> featureNames;
        /** 目标列名 */
        private String targetName;
        /** 类别名，回归任务为 null */
        private List<String> classNames;

        // ===== 线性回归 / 逻辑回归 / SVM =====
        /** 单特征时为 [斜率]，多特征时为归一化空间权重 */
        private List<Double> weights;
        /** 单特征时为截距 */
        private Double bias;
        /** 各特征的均值，前端在任意两个维度上切片画线时用来把其它维度固定在均值 */
        private List<Double> featureMeans;
        /** 回归直线或线性分类决策边界，形如 [[x1,y1],[x2,y2]] */
        private List<List<Double>> regressionLine;
        /** 非线性核的决策边界：可能有多条折线 */
        private List<List<List<Double>>> boundaryLines;

        // ===== K-Means =====
        /** 质心坐标（原始特征空间） */
        private List<List<Double>> centroids;

        // ===== PCA =====
        /** 主成分投影坐标 */
        private List<List<Double>> projections;
        /** 每个主成分解释的方差占比 */
        private List<Double> explainedVariance;
        /** 主成分载荷（每个主成分在各原始维度上的系数） */
        private List<List<Double>> pcaComponents;

        // ===== 决策树 =====
        private List<TreeNode> tree;
        /** 已展开的分裂数，前端据此画「正在生长的树」 */
        private Integer revealedSplits;
        /** 每个样本当前被预测的类别下标，前端据此给散点着色 */
        private List<Integer> assignments;

        /** 特征重要性（决策树 / 随机森林按分裂增益累计） */
        private List<FeatureImportance> featureImportances;
    }

    @Data
    public static class FeatureImportance {
        private String feature;
        private Double importance;
    }

    @Data
    public static class TreeNode {
        private Integer id;
        private Integer depth;
        /** null 表示叶子 */
        private String feature;
        private Integer featureIndex;
        private Double threshold;
        /** 节点自身的不纯度 */
        private Double impurity;
        private Integer samples;
        /** 节点的多数类 */
        private Integer classIndex;
        private Integer leftId;
        private Integer rightId;
        /** BFS 展开顺序，-1 表示永不分裂 */
        private Integer splitOrder;
    }
}
