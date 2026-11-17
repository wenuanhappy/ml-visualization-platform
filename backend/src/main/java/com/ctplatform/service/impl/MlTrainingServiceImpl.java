package com.ctplatform.service.impl;

import com.ctplatform.dto.ApiResponse;
import com.ctplatform.dto.MlDatasetDto;
import com.ctplatform.dto.MlProjectionDto;
import com.ctplatform.dto.MlTrainRequest;
import com.ctplatform.dto.MlTrainingResponse;
import com.ctplatform.entity.MlDataset;
import com.ctplatform.entity.MlTrainingSession;
import com.ctplatform.mapper.MlDatasetMapper;
import com.ctplatform.mapper.MlTrainingSessionMapper;
import com.ctplatform.service.MlAdvancedEngine;
import com.ctplatform.service.MlSimulationEngine;
import com.ctplatform.service.MlTrainingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 机器学习模块业务实现（第 08 组新增）。
 *
 * <p>会话只保存「参数 + 当前步数」，模型状态按步数确定性重算，
 * 因此任何一次请求的结果都与「从头单步走到这里」完全一致。</p>
 */
@Service
public class MlTrainingServiceImpl implements MlTrainingService {

    private static final String LINEAR_REGRESSION = "linear_regression";
    private static final String LOGISTIC_REGRESSION = "logistic_regression";
    private static final String SVM = "support_vector_machine";
    private static final String K_MEANS = "k_means";
    private static final String PCA = "pca";
    private static final String RANDOM_FOREST = "random_forest";
    private static final String DECISION_TREE = "decision_tree";

    /** 梯度下降类算法的固定步数 */
    private static final int GRADIENT_STEPS = 60;
    /** K-Means 固定迭代次数 */
    private static final int KMEANS_STEPS = 20;
    /** 随机森林固定树数 */
    private static final int FOREST_TREES = 8;

    private static final double DEFAULT_LEARNING_RATE = 0.1;
    private static final double DEFAULT_SVM_RATE = 0.01;
    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final int DEFAULT_K = 3;
    private static final int DEFAULT_COMPONENTS = 2;

    @Autowired
    private MlDatasetMapper datasetMapper;

    @Autowired
    private MlTrainingSessionMapper sessionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ApiResponse<List<MlDatasetDto.Summary>> listDatasets() {
        List<MlDataset> datasets = datasetMapper.selectList(null);
        List<MlDatasetDto.Summary> summaries = new ArrayList<>(datasets.size());
        for (MlDataset entity : datasets) {
            summaries.add(toSummary(entity));
        }
        return ApiResponse.success(summaries);
    }

    @Override
    public ApiResponse<MlDatasetDto> getDataset(Long id) {
        MlDataset entity = datasetMapper.selectById(id);
        if (entity == null) {
            return ApiResponse.error(404, "数据集不存在");
        }
        try {
            MlDatasetDto dto = new MlDatasetDto();
            dto.setId(entity.getId());
            dto.setCode(entity.getCode());
            dto.setName(entity.getName());
            dto.setDescription(entity.getDescription());
            dto.setAlgorithm(entity.getAlgorithm());
            dto.setFeatureNames(readStringList(entity.getFeatureNames()));
            dto.setTargetName(entity.getTargetName());
            dto.setClassNames(readStringList(entity.getClassNames()));
            dto.setPointCount(entity.getPointCount());
            dto.setPoints(readPoints(entity.getDataJson()));
            return ApiResponse.success(dto);
        } catch (Exception e) {
            return ApiResponse.error(500, "数据集解析失败: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse<MlTrainingResponse> createSession(MlTrainRequest request) {
        if (request == null || request.getDatasetId() == null) {
            return ApiResponse.badRequest("datasetId 不能为空");
        }
        MlDataset dataset = datasetMapper.selectById(request.getDatasetId());
        if (dataset == null) {
            return ApiResponse.error(404, "数据集不存在");
        }
        String algorithm = request.getAlgorithm() == null ? firstAlgorithm(dataset) : request.getAlgorithm();
        if (!isSupported(algorithm)) {
            return ApiResponse.badRequest("暂不支持的算法: " + algorithm);
        }
        // 与原项目一致：任意算法都可以选任意数据集，不匹配时由各算法自行降级
        // （例如回归用类别标签当数值目标、分类用目标值的中位数二分）
        try {
            MlSimulationEngine.Dataset parsed = toEngineDataset(dataset);
            int totalSteps = totalSteps(algorithm, parsed, request.getParameters());

            MlTrainingSession session = new MlTrainingSession();
            session.setAlgorithm(algorithm);
            session.setDatasetId(dataset.getId());
            session.setParameters(objectMapper.writeValueAsString(
                    request.getParameters() == null ? java.util.Map.of() : request.getParameters()));
            boolean oneShot = PCA.equals(algorithm);
            session.setCurrentStep(oneShot ? totalSteps : 0);
            session.setTotalSteps(totalSteps);
            session.setStatus(oneShot ? "finished" : "ready");
            sessionMapper.insert(session);
            return ApiResponse.success("训练会话已创建", buildResponse(session, dataset, parsed));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "创建训练会话失败: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse<MlTrainingResponse> getSession(Long sessionId) {
        MlTrainingSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return ApiResponse.error(404, "训练会话不存在");
        }
        return rebuild(session);
    }

    @Override
    public ApiResponse<MlTrainingResponse> step(Long sessionId) {
        MlTrainingSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return ApiResponse.error(404, "训练会话不存在");
        }
        int total = session.getTotalSteps() == null ? 0 : session.getTotalSteps();
        int next = Math.min(total, (session.getCurrentStep() == null ? 0 : session.getCurrentStep()) + 1);
        session.setCurrentStep(next);
        session.setStatus(next >= total ? "finished" : "training");
        sessionMapper.updateById(session);
        return rebuild(session);
    }

    @Override
    public ApiResponse<MlTrainingResponse> reset(Long sessionId) {
        MlTrainingSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return ApiResponse.error(404, "训练会话不存在");
        }
        session.setCurrentStep(0);
        session.setStatus("ready");
        sessionMapper.updateById(session);
        return rebuild(session);
    }

    private int totalSteps(String algorithm, MlSimulationEngine.Dataset parsed, java.util.Map<String, Object> params) {
        switch (algorithm) {
            case DECISION_TREE:
                return MlSimulationEngine.buildDecisionTree(parsed, intParam(params, "maxDepth", DEFAULT_MAX_DEPTH), 2)
                        .splitCount();
            case K_MEANS:
                return KMEANS_STEPS;
            case PCA:
                return Math.min(intParam(params, "nComponents", DEFAULT_COMPONENTS), parsed.featureCount());
            case RANDOM_FOREST:
                return intParam(params, "treeCount", FOREST_TREES);
            default:
                // 迭代次数允许前端调整，默认 60，上限 300（再大对课堂演示没有意义）
                return Math.max(5, Math.min(intParam(params, "steps", GRADIENT_STEPS), 300));
        }
    }

    private ApiResponse<MlTrainingResponse> rebuild(MlTrainingSession session) {
        MlDataset dataset = datasetMapper.selectById(session.getDatasetId());
        if (dataset == null) {
            return ApiResponse.error(404, "训练会话关联的数据集已不存在");
        }
        try {
            return ApiResponse.success(buildResponse(session, dataset, toEngineDataset(dataset)));
        } catch (Exception e) {
            return ApiResponse.error(500, "训练状态重建失败: " + e.getMessage());
        }
    }

    private MlTrainingResponse buildResponse(MlTrainingSession session, MlDataset dataset,
                                             MlSimulationEngine.Dataset parsed) throws Exception {
        int currentStep = session.getCurrentStep() == null ? 0 : session.getCurrentStep();
        int totalSteps = session.getTotalSteps() == null ? 0 : session.getTotalSteps();
        JsonNode params = objectMapper.readTree(
                session.getParameters() == null || session.getParameters().isBlank() ? "{}" : session.getParameters());

        MlTrainingResponse response = new MlTrainingResponse();
        response.setSessionId(session.getId());
        response.setAlgorithm(session.getAlgorithm());
        response.setDatasetId(dataset.getId());
        response.setDatasetName(dataset.getName());
        response.setCurrentStep(currentStep);
        response.setTotalSteps(totalSteps);
        response.setStatus(session.getStatus());

        MlTrainingResponse.ModelData model = new MlTrainingResponse.ModelData();
        model.setFeatureNames(parsed.featureNames());
        model.setTargetName(parsed.targetName());
        model.setClassNames(parsed.classNames());
        response.setModelData(model);

        switch (session.getAlgorithm()) {
            case LINEAR_REGRESSION -> fillLinearRegression(response, model, parsed, currentStep, params);
            case LOGISTIC_REGRESSION, SVM -> fillLinearClassifier(response, model, parsed, currentStep, params,
                    SVM.equals(session.getAlgorithm()));
            case K_MEANS -> fillKMeans(response, model, parsed, currentStep, params);
            case PCA -> fillPca(response, model, parsed, currentStep);
            case RANDOM_FOREST -> fillRandomForest(response, model, parsed, currentStep, params);
            case DECISION_TREE -> fillDecisionTree(response, model, parsed, currentStep, params);
            default -> {
                return response;
            }
        }
        return response;
    }

    private void fillLinearRegression(MlTrainingResponse response, MlTrainingResponse.ModelData model,
                                      MlSimulationEngine.Dataset parsed, int currentStep, JsonNode params) {
        response.setScoreLabel("R2");
        if (currentStep <= 0) {
            model.setWeights(List.of(0.0));
            model.setBias(0.0);
            response.setMetrics(List.of());
            return;
        }
        double learningRate = params.has("learningRate")
                ? params.get("learningRate").asDouble(DEFAULT_LEARNING_RATE) : DEFAULT_LEARNING_RATE;
        MlSimulationEngine.LinearRegressionResult result =
                MlSimulationEngine.trainLinearRegression(parsed, learningRate, currentStep);
        List<Double> weights = new ArrayList<>();
        for (double w : result.weights()) {
            weights.add(w);
        }
        model.setWeights(weights);
        model.setBias(result.bias());
        model.setFeatureMeans(toList(result.featureMeans()));
        model.setRegressionLine(result.regressionLine());
        response.setMetrics(toMetrics(result.history(), currentStep));
        MlSimulationEngine.MetricPoint last = result.history().get(result.history().size() - 1);
        response.setLoss(last.loss());
        response.setAccuracy(last.score());
    }

    private void fillLinearClassifier(MlTrainingResponse response, MlTrainingResponse.ModelData model,
                                      MlSimulationEngine.Dataset parsed, int currentStep, JsonNode params,
                                      boolean svm) {
        response.setScoreLabel("Accuracy");
        if (currentStep <= 0) {
            model.setWeights(List.of(0.0));
            model.setBias(0.0);
            response.setMetrics(List.of());
            return;
        }
        double defaultRate = svm ? DEFAULT_SVM_RATE : DEFAULT_LEARNING_RATE;
        double learningRate = params.has("learningRate") ? params.get("learningRate").asDouble(defaultRate) : defaultRate;
        String kernel = params.has("kernel") ? params.get("kernel").asText("linear") : "linear";
        double gamma = params.has("gamma")
                ? params.get("gamma").asDouble(0.5)
                : 1.0 / Math.max(1, parsed.featureCount());
        MlAdvancedEngine.ClassificationResult result;
        if (!svm) {
            result = MlAdvancedEngine.trainLogisticRegression(parsed, learningRate, currentStep);
        } else if ("linear".equalsIgnoreCase(kernel)) {
            result = MlAdvancedEngine.trainSupportVectorMachine(parsed, learningRate, currentStep);
        } else {
            result = MlAdvancedEngine.trainKernelSvm(parsed, kernel.toLowerCase(), gamma, currentStep);
        }
        List<Double> weights = new ArrayList<>();
        for (double w : result.weights()) {
            weights.add(w);
        }
        model.setWeights(weights);
        model.setBias(result.bias());
        model.setFeatureMeans(toList(result.featureMeans()));
        model.setRegressionLine(result.boundaryLine());
        model.setBoundaryLines(result.boundaryLines());
        model.setAssignments(result.predictions());
        response.setMetrics(toMetrics(result.history(), currentStep));
        MlSimulationEngine.MetricPoint last = result.history().get(result.history().size() - 1);
        response.setLoss(last.loss());
        response.setAccuracy(last.score());
    }

    private void fillKMeans(MlTrainingResponse response, MlTrainingResponse.ModelData model,
                            MlSimulationEngine.Dataset parsed, int currentStep, JsonNode params) {
        response.setScoreLabel("Silhouette");
        if (currentStep <= 0) {
            response.setMetrics(List.of());
            model.setCentroids(List.of());
            model.setAssignments(List.of());
            return;
        }
        int k = intParam(params, "k", DEFAULT_K);
        MlAdvancedEngine.ClusterResult result = MlAdvancedEngine.trainKMeans(parsed, k, currentStep);
        model.setCentroids(result.centroids());
        model.setAssignments(result.assignments());
        response.setMetrics(toMetrics(result.history(), currentStep));
        MlSimulationEngine.MetricPoint last = result.history().get(result.history().size() - 1);
        response.setLoss(last.loss());
        response.setAccuracy(last.score());
    }

    private void fillPca(MlTrainingResponse response, MlTrainingResponse.ModelData model,
                         MlSimulationEngine.Dataset parsed, int currentStep) {
        response.setScoreLabel("ExplainedVar");
        if (currentStep <= 0) {
            // 还没开始 / 已复位：不返回主成分，画布按原始维度显示
            model.setProjections(List.of());
            model.setExplainedVariance(List.of());
            model.setPcaComponents(List.of());
            response.setMetrics(List.of());
            response.setLoss(null);
            response.setAccuracy(null);
            return;
        }
        MlAdvancedEngine.PcaResult pca = MlAdvancedEngine.computePca(parsed, Math.max(1, currentStep));
        double cumulative = 0.0;
        List<List<Double>> projections = new ArrayList<>();
        for (List<Double> projection : pca.projections()) {
            projections.add(projection);
        }
        for (double ratio : pca.explainedVariance()) {
            cumulative += ratio;
        }
        model.setProjections(projections);
        model.setExplainedVariance(pca.explainedVariance());
        model.setPcaComponents(pca.components());
        response.setMetrics(List.of());
        response.setLoss(0.0);
        response.setAccuracy(Math.round(Math.min(1.0, cumulative) * 10000.0) / 10000.0);
    }

    private void fillRandomForest(MlTrainingResponse response, MlTrainingResponse.ModelData model,
                                  MlSimulationEngine.Dataset parsed, int currentStep, JsonNode params) {
        response.setScoreLabel("Accuracy");
        if (currentStep <= 0) {
            response.setMetrics(List.of());
            model.setAssignments(List.of());
            return;
        }
        int maxDepth = intParam(params, "maxDepth", DEFAULT_MAX_DEPTH);
        MlAdvancedEngine.ForestResult result =
                MlAdvancedEngine.trainRandomForest(parsed, intParam(params, "treeCount", FOREST_TREES), maxDepth, currentStep);
        model.setAssignments(result.assignments());
        model.setFeatureImportances(toImportances(result.importances(), parsed.featureNames()));
        model.setFeatureImportances(toImportances(result.importances(), parsed.featureNames()));
        response.setMetrics(toMetrics(result.history(), currentStep));
        MlSimulationEngine.MetricPoint last = result.history().get(result.history().size() - 1);
        response.setLoss(last.loss());
        response.setAccuracy(last.score());
    }

    private void fillDecisionTree(MlTrainingResponse response, MlTrainingResponse.ModelData model,
                                  MlSimulationEngine.Dataset parsed, int currentStep, JsonNode params) {
        response.setScoreLabel("Accuracy");
        int maxDepth = intParam(params, "maxDepth", DEFAULT_MAX_DEPTH);
        MlSimulationEngine.TreeResult tree = MlSimulationEngine.buildDecisionTree(parsed, maxDepth, 2);
        int revealed = Math.min(currentStep, tree.splitCount());
        List<MlTrainingResponse.TreeNode> nodes = new ArrayList<>(tree.nodes().size());
        for (MlSimulationEngine.Node node : tree.nodes()) {
            MlTrainingResponse.TreeNode dto = new MlTrainingResponse.TreeNode();
            dto.setId(node.id());
            dto.setDepth(node.depth());
            dto.setFeature(node.featureIndex() == null ? null : parsed.featureNames().get(node.featureIndex()));
            dto.setFeatureIndex(node.featureIndex());
            dto.setThreshold(node.threshold());
            dto.setImpurity(node.impurity());
            dto.setSamples(node.samples());
            dto.setClassIndex(node.classIndex());
            dto.setLeftId(node.leftId());
            dto.setRightId(node.rightId());
            dto.setSplitOrder(node.splitOrder());
            nodes.add(dto);
        }
        model.setTree(nodes);
        model.setRevealedSplits(revealed);
        // 一次分裂都没展开时不给预测结果，画布按数据集自带标签着色（原项目复位后也是这样）
        model.setAssignments(revealed <= 0
                ? List.of()
                : MlSimulationEngine.assignClasses(tree.nodes(), parsed.points(), revealed));
        response.setMetrics(toMetrics(tree.history(), revealed));
        if (revealed > 0 && !tree.history().isEmpty()) {
            MlSimulationEngine.MetricPoint last = tree.history().get(Math.min(revealed, tree.history().size() - 1));
            response.setLoss(last.loss());
            response.setAccuracy(last.score());
        }
    }

    /** 归一化并按重要性降序（对照原项目 forestImportances） */
    private List<MlTrainingResponse.FeatureImportance> toImportances(double[] values, List<String> names) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        List<MlTrainingResponse.FeatureImportance> importances = new ArrayList<>();
        for (int j = 0; j < values.length && j < names.size(); j++) {
            MlTrainingResponse.FeatureImportance item = new MlTrainingResponse.FeatureImportance();
            item.setFeature(names.get(j));
            item.setImportance(round4(total <= 0 ? 0.0 : values[j] / total));
            importances.add(item);
        }
        importances.sort((left, right) -> Double.compare(right.getImportance(), left.getImportance()));
        return importances;
    }

    private List<Double> toList(double[] values) {
        List<Double> list = new ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list;
    }

    // ==================== 投影：把模型切到「所选两个维度」上 ====================

    @Override
    public ApiResponse<MlProjectionDto> projection(Long sessionId, String xName, String yName) {
        MlTrainingSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return ApiResponse.error(404, "训练会话不存在");
        }
        MlDataset dataset = datasetMapper.selectById(session.getDatasetId());
        if (dataset == null) {
            return ApiResponse.error(404, "训练会话关联的数据集已不存在");
        }
        try {
            MlSimulationEngine.Dataset parsed = toEngineDataset(dataset);
            MlTrainingResponse response = buildResponse(session, dataset, parsed);
            MlTrainingResponse.ModelData model = response.getModelData();

            MlProjectionDto dto = new MlProjectionDto();
            dto.setSessionId(sessionId);
            dto.setXName(xName);
            dto.setYName(yName);

            switch (session.getAlgorithm()) {
                case DECISION_TREE -> projectDecisionTree(dto, parsed, model, xName, yName);
                case RANDOM_FOREST -> none(dto, "随机森林是多棵树投票，分区无法直接投影，散点按预测类别着色。");
                case K_MEANS -> projectCentroids(dto, parsed, model, xName, yName);
                case LINEAR_REGRESSION -> projectRegression(dto, parsed, model, xName, yName);
                case LOGISTIC_REGRESSION, SVM -> projectBoundary(dto, parsed, model, xName, yName);
                case PCA -> none(dto, "PCA 视图使用主成分投影，不再按原始维度画线。");
                default -> none(dto, "当前算法没有可投影的几何结果。");
            }
            return ApiResponse.success(dto);
        } catch (Exception e) {
            return ApiResponse.error(500, "投影计算失败: " + e.getMessage());
        }
    }

    private void none(MlProjectionDto dto, String note) {
        dto.setKind(MlProjectionDto.KIND_NONE);
        dto.setNote(note);
    }

    /** 回归：只有目标维度出现在坐标轴上时才能画线，否则只给提示 */
    private void projectRegression(MlProjectionDto dto, MlSimulationEngine.Dataset parsed,
                                   MlTrainingResponse.ModelData model, String xName, String yName) {
        String targetName = parsed.targetName();
        boolean targetOnX = targetName != null && targetName.equals(xName);
        boolean targetOnY = targetName != null && targetName.equals(yName);
        if (!targetOnX && !targetOnY) {
            none(dto, "目标维度 " + (targetName == null ? "目标列" : targetName)
                    + " 不在当前坐标轴上，无法投影回归线；请把目标维度设为其中一条轴。");
            return;
        }
        String featureName = targetOnX ? yName : xName;
        int featureIndex = parsed.featureNames().indexOf(featureName);
        if (featureIndex < 0) {
            none(dto, "所选维度不是数值特征，无法投影回归线。");
            return;
        }
        List<Double> weights = model.getWeights();
        List<Double> means = model.getFeatureMeans();
        if (weights == null || means == null || weights.isEmpty()) {
            none(dto, "当前模型还没有可投影的回归结果。");
            return;
        }
        double bias = model.getBias() == null ? 0.0 : model.getBias();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (MlSimulationEngine.Point point : parsed.points()) {
            min = Math.min(min, point.features()[featureIndex]);
            max = Math.max(max, point.features()[featureIndex]);
        }
        List<List<Double>> line = new ArrayList<>(2);
        for (double x : new double[]{min, max}) {
            double y = bias + weights.get(featureIndex) * x;
            for (int j = 0; j < weights.size(); j++) {
                if (j != featureIndex) {
                    y += weights.get(j) * means.get(j);
                }
            }
            if (targetOnX) {
                line.add(List.of(round4(y), round4(x)));
            } else {
                line.add(List.of(round4(x), round4(y)));
            }
        }
        dto.setKind(MlProjectionDto.KIND_REGRESSION_LINE);
        dto.setLines(List.of(line));
        dto.setNote("隐藏维度固定在各自均值上；当前只显示「" + featureName + "」与目标的关系。");
    }

    /** 线性分类：其余维度固定在均值后仍是一条直线 */
    private void projectBoundary(MlProjectionDto dto, MlSimulationEngine.Dataset parsed,
                                 MlTrainingResponse.ModelData model, String xName, String yName) {
        int xIndex = parsed.featureNames().indexOf(xName);
        int yIndex = parsed.featureNames().indexOf(yName);
        // 非线性核（poly / rbf）的边界是采样出来的多段折线，直接返回，不能按线性方程求解
        if (model.getBoundaryLines() != null && !model.getBoundaryLines().isEmpty()
                && xIndex == 0 && yIndex == 1) {
            dto.setKind(MlProjectionDto.KIND_DECISION_BOUNDARY);
            dto.setLines(model.getBoundaryLines());
            dto.setNote("非线性核（poly / rbf）的决策边界，由网格采样得到。");
            return;
        }
        if (xIndex < 0 || yIndex < 0) {
            none(dto, "决策边界只能投影到两个数值特征维度上。");
            return;
        }
        List<Double> weights = model.getWeights();
        List<Double> means = model.getFeatureMeans();
        if (weights == null || means == null || weights.size() <= Math.max(xIndex, yIndex)) {
            none(dto, "当前权重在这两个维度上无法确定边界。");
            return;
        }
        double wy = weights.get(yIndex);
        if (Math.abs(wy) < 1e-9) {
            none(dto, "该维度上的系数接近 0，边界无法写成 y = f(x)。");
            return;
        }
        double bias = model.getBias() == null ? 0.0 : model.getBias();
        double rest = bias;
        for (int j = 0; j < weights.size(); j++) {
            if (j != xIndex && j != yIndex) {
                rest += weights.get(j) * means.get(j);
            }
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (MlSimulationEngine.Point point : parsed.points()) {
            min = Math.min(min, point.features()[xIndex]);
            max = Math.max(max, point.features()[xIndex]);
        }
        double wx = weights.get(xIndex);
        List<List<Double>> line = List.of(
                List.of(round4(min), round4(-(rest + wx * min) / wy)),
                List.of(round4(max), round4(-(rest + wx * max) / wy)));
        dto.setKind(MlProjectionDto.KIND_DECISION_BOUNDARY);
        dto.setLines(List.of(line));
        dto.setNote("隐藏维度固定在各自均值上；直线表示概率 0.5 的分界。");
    }

    /** 聚类：质心直接取所选两个维度的坐标 */
    private void projectCentroids(MlProjectionDto dto, MlSimulationEngine.Dataset parsed,
                                  MlTrainingResponse.ModelData model, String xName, String yName) {
        int xIndex = parsed.featureNames().indexOf(xName);
        int yIndex = parsed.featureNames().indexOf(yName);
        if (xIndex < 0 || yIndex < 0) {
            none(dto, "聚类中心只能投影到两个数值特征维度上。");
            return;
        }
        List<List<Double>> centroids = new ArrayList<>();
        for (List<Double> centroid : model.getCentroids() == null ? List.<List<Double>>of() : model.getCentroids()) {
            if (centroid.size() > Math.max(xIndex, yIndex)) {
                centroids.add(List.of(round4(centroid.get(xIndex)), round4(centroid.get(yIndex))));
            }
        }
        if (centroids.isEmpty()) {
            none(dto, "还没有质心，先步进一次再投影。");
            return;
        }
        dto.setKind(MlProjectionDto.KIND_CENTROIDS);
        dto.setCentroids(centroids);
        dto.setNote("簇编号来自全维聚类结果，换坐标轴不影响归属。");
    }

    /** 决策树：把已展开的分裂切成线段（每个分裂一段） */
    private void projectDecisionTree(MlProjectionDto dto, MlSimulationEngine.Dataset parsed,
                                     MlTrainingResponse.ModelData model, String xName, String yName) {
        int xIndex = parsed.featureNames().indexOf(xName);
        int yIndex = parsed.featureNames().indexOf(yName);
        List<MlTrainingResponse.TreeNode> nodes = model.getTree();
        if (xIndex < 0 || yIndex < 0 || nodes == null || nodes.isEmpty()) {
            none(dto, "决策树的分裂只能画在两个数值特征维度上。");
            return;
        }
        int revealed = model.getRevealedSplits() == null ? 0 : model.getRevealedSplits();
        if (revealed <= 0) {
            none(dto, "还没有展开任何分裂，先步进一次。");
            return;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (MlSimulationEngine.Point point : parsed.points()) {
            minX = Math.min(minX, point.features()[xIndex]);
            maxX = Math.max(maxX, point.features()[xIndex]);
            minY = Math.min(minY, point.features()[yIndex]);
            maxY = Math.max(maxY, point.features()[yIndex]);
        }

        List<double[]> partitions = new ArrayList<>();
        // {nodeId, x1, x2, y1, y2}
        partitions.add(new double[]{0, minX, maxX, minY, maxY});
        List<List<List<Double>>> segments = new ArrayList<>();

        List<MlTrainingResponse.TreeNode> splits = new ArrayList<>();
        for (MlTrainingResponse.TreeNode node : nodes) {
            if (node.getSplitOrder() != null && node.getSplitOrder() >= 0 && node.getSplitOrder() < revealed) {
                splits.add(node);
            }
        }
        splits.sort(java.util.Comparator.comparingInt(MlTrainingResponse.TreeNode::getSplitOrder));

        for (MlTrainingResponse.TreeNode split : splits) {
            int index = -1;
            for (int i = 0; i < partitions.size(); i++) {
                if ((int) partitions.get(i)[0] == split.getId()) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                continue;
            }
            double[] part = partitions.remove(index);
            if (split.getFeatureIndex() != null && split.getFeatureIndex() == xIndex) {
                segments.add(List.of(
                        List.of(round4(split.getThreshold()), round4(part[3])),
                        List.of(round4(split.getThreshold()), round4(part[4]))));
                partitions.add(new double[]{split.getLeftId(), part[1], split.getThreshold(), part[3], part[4]});
                partitions.add(new double[]{split.getRightId(), split.getThreshold(), part[2], part[3], part[4]});
            } else if (split.getFeatureIndex() != null && split.getFeatureIndex() == yIndex) {
                segments.add(List.of(
                        List.of(round4(part[1]), round4(split.getThreshold())),
                        List.of(round4(part[2]), round4(split.getThreshold()))));
                partitions.add(new double[]{split.getLeftId(), part[1], part[2], part[3], split.getThreshold()});
                partitions.add(new double[]{split.getRightId(), part[1], part[2], split.getThreshold(), part[4]});
            } else {
                partitions.add(part);
            }
        }

        if (segments.isEmpty()) {
            none(dto, "当前已展开的分裂都不在这两个维度上，换一组坐标轴试试。");
            return;
        }
        dto.setKind(MlProjectionDto.KIND_DECISION_BOUNDARY);
        dto.setLines(segments);
        dto.setNote("每条虚线是一次分裂的阈值，只在它所属的区域内延伸。");
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    // ==================== 对比 / 特征选择 ====================

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<List<java.util.Map<String, Object>>> comparison(java.util.Map<String, Object> body) {
        Long datasetId = asLong(body.get("datasetId"));
        if (datasetId == null) {
            return ApiResponse.badRequest("datasetId 不能为空");
        }
        MlDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            return ApiResponse.error(404, "数据集不存在");
        }
        Object rawConfigs = body.get("configs");
        if (!(rawConfigs instanceof List<?> configs) || configs.isEmpty()) {
            return ApiResponse.badRequest("configs 不能为空");
        }
        try {
            MlSimulationEngine.Dataset parsed = toEngineDataset(dataset);
            List<java.util.Map<String, Object>> items = new ArrayList<>();
            for (Object raw : configs) {
                java.util.Map<String, Object> config = (java.util.Map<String, Object>) raw;
                String algorithm = String.valueOf(config.get("algorithm"));
                String alias = config.get("alias") == null ? algorithm : String.valueOf(config.get("alias"));
                java.util.Map<String, Object> parameters = config.get("parameters") instanceof java.util.Map
                        ? (java.util.Map<String, Object>) config.get("parameters")
                        : java.util.Map.of();
                if (!isSupported(algorithm)) {
                    continue;
                }
                int totalSteps = totalSteps(algorithm, parsed, parameters);
                MlTrainingSession transientSession = new MlTrainingSession();
                transientSession.setAlgorithm(algorithm);
                transientSession.setDatasetId(dataset.getId());
                transientSession.setParameters(objectMapper.writeValueAsString(parameters));
                transientSession.setCurrentStep(Math.max(1, totalSteps));
                transientSession.setTotalSteps(totalSteps);
                transientSession.setStatus("finished");
                MlTrainingResponse response = buildResponse(transientSession, dataset, parsed);

                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("alias", alias);
                item.put("algorithm", algorithm);
                item.put("accuracy", response.getAccuracy());
                item.put("loss", response.getLoss());
                item.put("scoreLabel", response.getScoreLabel());
                item.put("totalSteps", totalSteps);
                items.add(item);
            }
            return ApiResponse.success(items);
        } catch (Exception e) {
            return ApiResponse.error(500, "对比计算失败: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<MlTrainingResponse> featureSelection(java.util.Map<String, Object> body) {
        Long datasetId = asLong(body.get("datasetId"));
        String algorithm = body.get("algorithm") == null ? null : String.valueOf(body.get("algorithm"));
        if (datasetId == null || algorithm == null) {
            return ApiResponse.badRequest("datasetId / algorithm 不能为空");
        }
        MlDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            return ApiResponse.error(404, "数据集不存在");
        }
        try {
            MlSimulationEngine.Dataset full = toEngineDataset(dataset);
            List<String> selected = new ArrayList<>();
            if (body.get("features") instanceof List<?> features) {
                for (Object feature : features) {
                    selected.add(String.valueOf(feature));
                }
            }
            MlSimulationEngine.Dataset filtered = filterFeatures(full, selected);
            java.util.Map<String, Object> parameters = body.get("parameters") instanceof java.util.Map
                    ? (java.util.Map<String, Object>) body.get("parameters")
                    : java.util.Map.of();
            int totalSteps = totalSteps(algorithm, filtered, parameters);

            MlTrainingSession transientSession = new MlTrainingSession();
            transientSession.setAlgorithm(algorithm);
            transientSession.setDatasetId(dataset.getId());
            transientSession.setParameters(objectMapper.writeValueAsString(parameters));
            transientSession.setCurrentStep(Math.max(1, totalSteps));
            transientSession.setTotalSteps(totalSteps);
            transientSession.setStatus("finished");
            return ApiResponse.success(buildResponse(transientSession, dataset, filtered));
        } catch (Exception e) {
            return ApiResponse.error(500, "特征选择训练失败: " + e.getMessage());
        }
    }

    /** 只保留选中的特征维度；回归目标与标签不受影响 */
    private MlSimulationEngine.Dataset filterFeatures(MlSimulationEngine.Dataset dataset, List<String> selected) {
        if (selected == null || selected.isEmpty()) {
            return dataset;
        }
        List<Integer> indices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String name : selected) {
            int index = dataset.featureNames().indexOf(name);
            if (index >= 0) {
                indices.add(index);
                names.add(name);
            }
        }
        if (indices.isEmpty()) {
            return dataset;
        }
        List<MlSimulationEngine.Point> points = new ArrayList<>(dataset.points().size());
        for (MlSimulationEngine.Point point : dataset.points()) {
            double[] features = new double[indices.size()];
            for (int i = 0; i < indices.size(); i++) {
                features[i] = point.features()[indices.get(i)];
            }
            points.add(new MlSimulationEngine.Point(features, point.target(), point.label()));
        }
        return new MlSimulationEngine.Dataset(names, dataset.targetName(), dataset.classNames(), points);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    @Override
    public ApiResponse<MlDatasetDto.Summary> uploadDataset(String name, String description,
                                                           org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.badRequest("文件为空");
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rows.add(line.split(",", -1));
                if (rows.size() > 2000) {
                    break;
                }
            }
            if (rows.size() < 2) {
                return ApiResponse.badRequest("CSV 至少需要表头与一行数据");
            }

            boolean hasHeader = !isNumeric(rows.get(0)[0]);
            List<String> header = new ArrayList<>();
            int columnCount = rows.get(0).length;
            for (int j = 0; j < columnCount; j++) {
                header.add(hasHeader ? rows.get(0)[j].trim() : "特征" + (j + 1));
            }
            int startRow = hasHeader ? 1 : 0;

            // 最后一列：全为数值 → 当回归目标；否则当类别标签
            int lastIndex = columnCount - 1;
            boolean lastNumeric = true;
            for (int i = startRow; i < rows.size(); i++) {
                if (!isNumeric(rows.get(i)[lastIndex])) {
                    lastNumeric = false;
                    break;
                }
            }

            List<Integer> featureIndexes = new ArrayList<>();
            for (int j = 0; j < lastIndex; j++) {
                boolean numeric = true;
                for (int i = startRow; i < rows.size(); i++) {
                    if (!isNumeric(rows.get(i)[j])) {
                        numeric = false;
                        break;
                    }
                }
                if (numeric) {
                    featureIndexes.add(j);
                }
            }
            if (featureIndexes.isEmpty()) {
                return ApiResponse.badRequest("没有可用的数值特征列");
            }

            List<String> featureNames = new ArrayList<>();
            for (int index : featureIndexes) {
                featureNames.add(header.get(index));
            }
            List<String> classNames = new ArrayList<>();
            List<java.util.Map<String, Object>> points = new ArrayList<>();
            for (int i = startRow; i < rows.size(); i++) {
                String[] cells = rows.get(i);
                if (cells.length < columnCount) {
                    continue;
                }
                List<Double> features = new ArrayList<>(featureIndexes.size());
                for (int index : featureIndexes) {
                    features.add(Double.parseDouble(cells[index].trim()));
                }
                java.util.Map<String, Object> point = new java.util.LinkedHashMap<>();
                point.put("features", features);
                if (lastNumeric) {
                    point.put("target", Double.parseDouble(cells[lastIndex].trim()));
                } else {
                    String label = cells[lastIndex].trim();
                    int classIndex = classNames.indexOf(label);
                    if (classIndex < 0) {
                        classNames.add(label);
                        classIndex = classNames.size() - 1;
                    }
                    point.put("label", classIndex);
                }
                points.add(point);
            }
            if (points.isEmpty()) {
                return ApiResponse.badRequest("没有解析到有效数据行");
            }

            MlDataset entity = new MlDataset();
            entity.setCode("upload_" + System.currentTimeMillis());
            entity.setName(name == null || name.isBlank() ? file.getOriginalFilename() : name);
            entity.setDescription(description == null || description.isBlank()
                    ? "上传的 CSV 数据集" : description);
            entity.setAlgorithm("linear_regression,decision_tree,random_forest,logistic_regression,"
                    + "support_vector_machine,k_means,pca");
            entity.setFeatureNames(objectMapper.writeValueAsString(featureNames));
            entity.setTargetName(lastNumeric ? header.get(lastIndex) : header.get(lastIndex));
            if (!classNames.isEmpty()) {
                entity.setClassNames(objectMapper.writeValueAsString(classNames));
            }
            entity.setPointCount(points.size());
            entity.setDataJson(objectMapper.writeValueAsString(points));
            datasetMapper.insert(entity);
            return ApiResponse.success("数据集已上传", toSummary(entity));
        } catch (Exception e) {
            return ApiResponse.error(500, "CSV 解析失败: " + e.getMessage());
        }
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== AI 助教 ====================

    @org.springframework.beans.factory.annotation.Value("${llm.endpoint:https://api.openai.com/v1}")
    private String llmEndpoint;

    @org.springframework.beans.factory.annotation.Value("${llm.model:gpt-3.5-turbo}")
    private String llmModel;

    @org.springframework.beans.factory.annotation.Value("${llm.api-key:}")
    private String llmApiKey;

    @Override
    public String askAssistant(String question, String context) {
        if (question == null || question.isBlank()) {
            return "请先输入问题。";
        }
        if (llmApiKey == null || llmApiKey.isBlank()) {
            return "AI 助教尚未配置：请设置环境变量 LLM_API_KEY（可选 LLM_ENDPOINT / LLM_MODEL）后重启后端。";
        }
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("model", llmModel);
            payload.put("temperature", 0.4);
            java.util.List<java.util.Map<String, String>> messages = new ArrayList<>();
            messages.add(java.util.Map.of("role", "system",
                    "content", "你是机器学习教学平台的助教，用中文简洁回答，结合当前实验上下文。"));
            messages.add(java.util.Map.of("role", "user",
                    "content", (context == null || context.isBlank() ? "" : "当前实验：" + context + "\n") + question));
            payload.put("messages", messages);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmApiKey);
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(payload, headers);

            String url = llmEndpoint.replaceAll("/$", "") + "/chat/completions";
            JsonNode response = objectMapper.readTree(
                    restTemplate.postForObject(url, entity, String.class));
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? "AI 没有返回内容，请稍后再试。" : content.asText();
        } catch (Exception e) {
            return "AI 助教调用失败：" + e.getMessage();
        }
    }

    private List<MlTrainingResponse.MetricPoint> toMetrics(List<MlSimulationEngine.MetricPoint> history, int upto) {
        List<MlTrainingResponse.MetricPoint> metrics = new ArrayList<>();
        for (MlSimulationEngine.MetricPoint point : history) {
            if (point.step() > upto) {
                break;
            }
            MlTrainingResponse.MetricPoint dto = new MlTrainingResponse.MetricPoint();
            dto.setStep(point.step());
            dto.setLoss(point.loss());
            dto.setScore(point.score());
            metrics.add(dto);
        }
        return metrics;
    }

    private MlDatasetDto.Summary toSummary(MlDataset entity) {
        MlDatasetDto.Summary summary = new MlDatasetDto.Summary();
        summary.setId(entity.getId());
        summary.setCode(entity.getCode());
        summary.setName(entity.getName());
        summary.setDescription(entity.getDescription());
        summary.setAlgorithm(entity.getAlgorithm());
        summary.setTargetName(entity.getTargetName());
        summary.setPointCount(entity.getPointCount());
        try {
            summary.setFeatureNames(readStringList(entity.getFeatureNames()));
            summary.setClassNames(readStringList(entity.getClassNames()));
        } catch (Exception ignored) {
            // 元数据损坏时不影响列表展示
        }
        return summary;
    }

    private MlSimulationEngine.Dataset toEngineDataset(MlDataset entity) throws Exception {
        List<String> featureNames = readStringList(entity.getFeatureNames());
        List<String> classNames = readStringList(entity.getClassNames());
        List<MlDatasetDto.PointDto> source = readPoints(entity.getDataJson());

        // 数据集与算法不强制匹配：没有 target 时用类别当数值目标，没有 label 时按目标中位数二分
        List<Double> targets = new ArrayList<>();
        for (MlDatasetDto.PointDto point : source) {
            if (point.getTarget() != null) {
                targets.add(point.getTarget());
            }
        }
        Double median = null;
        if (!targets.isEmpty()) {
            List<Double> sorted = new ArrayList<>(targets);
            java.util.Collections.sort(sorted);
            median = sorted.get(sorted.size() / 2);
        }

        List<MlSimulationEngine.Point> points = new ArrayList<>();
        for (MlDatasetDto.PointDto point : source) {
            double[] features = new double[point.getFeatures().size()];
            for (int i = 0; i < features.length; i++) {
                features[i] = point.getFeatures().get(i);
            }
            Double target = point.getTarget();
            Integer label = point.getLabel();
            if (target == null && label != null) {
                target = label.doubleValue();
            }
            if (label == null && target != null && median != null) {
                label = target > median ? 1 : 0;
            }
            points.add(new MlSimulationEngine.Point(features, target, label));
        }
        if (classNames == null) {
            classNames = List.of("类别 0", "类别 1");
        }
        return new MlSimulationEngine.Dataset(featureNames, entity.getTargetName(), classNames, points);
    }

    private List<MlDatasetDto.PointDto> readPoints(String dataJson) throws Exception {
        List<MlDatasetDto.PointDto> points = new ArrayList<>();
        JsonNode array = objectMapper.readTree(dataJson == null ? "[]" : dataJson);
        for (JsonNode node : array) {
            MlDatasetDto.PointDto point = new MlDatasetDto.PointDto();
            List<Double> features = new ArrayList<>();
            for (JsonNode feature : node.withArray("features")) {
                features.add(feature.asDouble());
            }
            point.setFeatures(features);
            point.setTarget(node.hasNonNull("target") ? node.get("target").asDouble() : null);
            point.setLabel(node.hasNonNull("label") ? node.get("label").asInt() : null);
            points.add(point);
        }
        return points;
    }

    private List<String> readStringList(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : objectMapper.readTree(json)) {
            values.add(node.asText());
        }
        return values;
    }

    private int intParam(JsonNode params, String key, int defaultValue) {
        return params != null && params.has(key) ? params.get(key).asInt(defaultValue) : defaultValue;
    }

    private int intParam(java.util.Map<String, Object> parameters, String key, int defaultValue) {
        if (parameters == null) {
            return defaultValue;
        }
        Object value = parameters.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private boolean isSupported(String algorithm) {
        return switch (algorithm) {
            case LINEAR_REGRESSION, LOGISTIC_REGRESSION, SVM, K_MEANS, PCA, RANDOM_FOREST, DECISION_TREE -> true;
            default -> false;
        };
    }

    /** 数据集的 algorithm 字段支持逗号分隔的多个算法 */
    private boolean supports(MlDataset dataset, String algorithm) {
        String value = dataset.getAlgorithm();
        if (value == null) {
            return false;
        }
        for (String item : value.split(",")) {
            if (item.trim().equals(algorithm)) {
                return true;
            }
        }
        return false;
    }

    private String firstAlgorithm(MlDataset dataset) {
        String value = dataset.getAlgorithm();
        return value == null ? "" : value.split(",")[0].trim();
    }
}
