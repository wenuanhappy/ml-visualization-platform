package com.ctplatform.service;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器学习仿真核心（第 08 组新增）。
 *
 * <p>纯计算类：不依赖 Spring、不访问数据库，便于单元测试。
 * 移植自本组原项目 {@code TrainingSimulationService}，保留了其中两个算法的原始语义：</p>
 * <ul>
 *   <li><b>线性回归</b>：特征与目标都做标准化，全量梯度下降 + 0.35 动量；损失为 MSE，得分为 R²。</li>
 *   <li><b>决策树</b>：基尼不纯度贪心分裂、BFS 顺序编号，一次「步进」展开一个分裂（树一层层长）。</li>
 * </ul>
 *
 * <p>与原实现的关键差异：原实现把模型快照逐步写进 session 表，这里改为
 * <b>由参数确定性地重算到第 N 步</b>。样本量在千级以下时开销可忽略，
 * 换来的是无漂移、可重放、重置免费。</p>
 */
public final class MlSimulationEngine {

    private static final double EPS = 1e-9;
    private static final double MOMENTUM = 0.35;
    private static final int MAX_TREE_DEPTH = 5;
    private static final int MIN_SAMPLES_PER_LEAF = 2;

    private MlSimulationEngine() {
    }

    /** 样本点：多特征 + 可选数值目标（回归）或类别下标（分类）。 */
    public record Point(double[] features, Double target, Integer label) {
    }

    /** 数据集。 */
    public record Dataset(List<String> featureNames, String targetName, List<String> classNames, List<Point> points) {

        public int featureCount() {
            return featureNames.size();
        }
    }

    /** 单步指标：step 从 1 开始计数。 */
    public record MetricPoint(int step, double loss, double score) {
    }

    /** 线性回归训练结果。 */
    public record LinearRegressionResult(double[] weights, double bias,
                                         double[] featureMeans,
                                         List<List<Double>> regressionLine,
                                         List<Double> predictions,
                                         List<MetricPoint> history) {
    }

    /** 决策树节点。 */
    public record Node(int id, int depth, Integer featureIndex, Double threshold,
                       double impurity, int samples, int classIndex,
                       Integer leftId, Integer rightId, int splitOrder) {
    }

    /** 决策树构建结果。 */
    public record TreeResult(List<Node> nodes, int splitCount, List<MetricPoint> history) {
    }

    // ==================== 线性回归 ====================

    /**
     * 训练线性回归并返回全部历史，第 k 步的状态是 {@code history.get(k-1)}。
     *
     * @param learningRate 学习率，会被裁剪到 [0.0001, 0.2]
     * @param steps        总步数，至少 1
     */
    public static LinearRegressionResult trainLinearRegression(Dataset dataset, double learningRate, int steps) {
        List<Point> points = dataset.points();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("数据集没有样本。" );
        }
        int featureCount = dataset.featureCount();
        int n = points.size();

        double[] means = new double[featureCount];
        double[] scales = new double[featureCount];
        for (int j = 0; j < featureCount; j++) {
            double sum = 0.0;
            for (Point p : points) {
                sum += p.features()[j];
            }
            means[j] = sum / n;
            double varSum = 0.0;
            for (Point p : points) {
                double d = p.features()[j] - means[j];
                varSum += d * d;
            }
            scales[j] = Math.sqrt(varSum / n);
        }

        double targetMean = 0.0;
        for (Point p : points) {
            targetMean += requireTarget(p);
        }
        targetMean /= n;
        double targetVar = 0.0;
        for (Point p : points) {
            double d = requireTarget(p) - targetMean;
            targetVar += d * d;
        }
        double targetScale = Math.sqrt(targetVar / n);

        double lr = clamp(learningRate, 0.0001, 0.2);
        double[] weights = new double[featureCount];
        double bias = 0.0;
        List<MetricPoint> history = new ArrayList<>();
        int totalSteps = Math.max(1, steps);

        for (int step = 1; step <= totalSteps; step++) {
            double[] gradient = new double[featureCount];
            double gradientBias = 0.0;
            for (Point p : points) {
                double prediction = dotNormalized(p.features(), means, scales, weights) + bias;
                double error = prediction - normalize(requireTarget(p), targetMean, targetScale);
                for (int j = 0; j < featureCount; j++) {
                    gradient[j] += 2.0 * error * normalize(p.features()[j], means[j], scales[j]);
                }
                gradientBias += 2.0 * error;
            }
            for (int j = 0; j < featureCount; j++) {
                double target = weights[j] - lr * gradient[j] / n;
                weights[j] = weights[j] + (target - weights[j]) * MOMENTUM;
            }
            double targetBias = bias - lr * gradientBias / n;
            bias = bias + (targetBias - bias) * MOMENTUM;

            double loss = 0.0;
            double sse = 0.0;
            double sst = 0.0;
            for (Point p : points) {
                double normalizedPrediction = dotNormalized(p.features(), means, scales, weights) + bias;
                double normalizedError = normalizedPrediction - normalize(requireTarget(p), targetMean, targetScale);
                loss += normalizedError * normalizedError;
                double rawError = denormalize(normalizedPrediction, targetMean, targetScale) - requireTarget(p);
                sse += rawError * rawError;
                double centered = requireTarget(p) - targetMean;
                sst += centered * centered;
            }
            double mse = loss / n;
            double r2 = sst < EPS ? 1.0 : 1.0 - sse / sst;
            history.add(new MetricPoint(step, round(mse), round(clamp(r2, 0.0, 0.99))));
        }

        List<Double> predictions = new ArrayList<>(n);
        for (Point p : points) {
            double normalizedPrediction = dotNormalized(p.features(), means, scales, weights) + bias;
            predictions.add(round(denormalize(normalizedPrediction, targetMean, targetScale)));
        }

        // 把归一化空间的系数换算回原始特征空间：前端据此可以在任意两个维度上切片画线
        double[] rawWeights = new double[featureCount];
        for (int j = 0; j < featureCount; j++) {
            rawWeights[j] = weights[j] * targetScale / Math.max(EPS, scales[j]);
        }
        double rawBias = targetMean + bias * targetScale;
        for (int j = 0; j < featureCount; j++) {
            rawBias -= rawWeights[j] * means[j];
        }

        List<List<Double>> regressionLine = null;
        if (featureCount == 1) {
            double minX = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            for (Point p : points) {
                minX = Math.min(minX, p.features()[0]);
                maxX = Math.max(maxX, p.features()[0]);
            }
            regressionLine = List.of(
                    List.of(round(minX), round(rawWeights[0] * minX + rawBias)),
                    List.of(round(maxX), round(rawWeights[0] * maxX + rawBias)));
        }

        return new LinearRegressionResult(rawWeights, round(rawBias), means.clone(),
                regressionLine, predictions, history);
    }

    // ==================== 决策树 ====================

    /**
     * 构建完整的贪心决策树。返回的节点按 BFS 顺序编号，{@code splitOrder} 决定步进时的展开顺序。
     */
    public static TreeResult buildDecisionTree(Dataset dataset, int maxDepth, int minSamplesPerLeaf) {
        List<Point> points = dataset.points();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("数据集没有样本。");
        }
        int classCount = resolveClassCount(dataset);
        int depthLimit = Math.max(1, Math.min(maxDepth, MAX_TREE_DEPTH));
        int minSamples = Math.max(1, Math.min(minSamplesPerLeaf, MIN_SAMPLES_PER_LEAF * 10));

        List<NodeBuilder> builders = new ArrayList<>();
        List<List<Point>> rowsOf = new ArrayList<>();
        NodeBuilder root = new NodeBuilder(0, 0, points, classCount);
        builders.add(root);
        rowsOf.add(points);

        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        int splitOrder = 0;

        while (!queue.isEmpty()) {
            int index = queue.poll();
            NodeBuilder node = builders.get(index);
            if (node.depth >= depthLimit
                    || node.rows.size() < 2 * minSamples
                    || node.impurity < EPS) {
                continue;
            }
            Split split = bestSplit(node.rows, dataset.featureCount(), classCount, minSamples);
            if (split == null || split.gain() <= EPS) {
                continue;
            }
            List<Point> leftRows = new ArrayList<>();
            List<Point> rightRows = new ArrayList<>();
            for (Point p : node.rows) {
                if (p.features()[split.featureIndex()] <= split.threshold()) {
                    leftRows.add(p);
                } else {
                    rightRows.add(p);
                }
            }
            node.featureIndex = split.featureIndex();
            node.threshold = split.threshold();
            node.splitOrder = splitOrder++;

            NodeBuilder left = new NodeBuilder(builders.size(), node.depth + 1, leftRows, classCount);
            builders.add(left);
            rowsOf.add(leftRows);
            node.leftId = left.id;

            NodeBuilder right = new NodeBuilder(builders.size(), node.depth + 1, rightRows, classCount);
            builders.add(right);
            rowsOf.add(rightRows);
            node.rightId = right.id;

            queue.add(left.id);
            queue.add(right.id);
        }

        List<Node> nodes = new ArrayList<>(builders.size());
        for (NodeBuilder b : builders) {
            nodes.add(new Node(b.id, b.depth, b.featureIndex, b.threshold, round(b.impurity),
                    b.rows.size(), b.classIndex, b.leftId, b.rightId, b.splitOrder));
        }

        int totalSplits = splitOrder;
        List<MetricPoint> history = new ArrayList<>();
        for (int step = 0; step <= totalSplits; step++) {
            history.add(metricOf(nodes, points, step));
        }
        return new TreeResult(nodes, totalSplits, history);
    }

    /** 按已展开的分裂数，给每个样本算出当前预测类别。 */
    public static List<Integer> assignClasses(List<Node> nodes, List<Point> points, int revealed) {
        List<Integer> assignments = new ArrayList<>(points.size());
        for (Point p : points) {
            assignments.add(terminalNode(nodes, revealed, p).classIndex());
        }
        return assignments;
    }

    private static MetricPoint metricOf(List<Node> nodes, List<Point> points, int revealed) {
        int correct = 0;
        double impuritySum = 0.0;
        for (Point p : points) {
            Node terminal = terminalNode(nodes, revealed, p);
            if (terminal.classIndex() == p.label()) {
                correct++;
            }
            impuritySum += terminal.impurity();
        }
        double accuracy = points.isEmpty() ? 0.0 : (double) correct / points.size();
        double averageImpurity = points.isEmpty() ? 0.0 : impuritySum / points.size();
        return new MetricPoint(revealed, round(averageImpurity), round(accuracy));
    }

    private static Node terminalNode(List<Node> nodes, int revealed, Point p) {
        Node current = nodes.get(0);
        while (true) {
            if (current.featureIndex() == null || current.splitOrder() < 0 || current.splitOrder() >= revealed) {
                return current;
            }
            Node left = nodes.get(current.leftId());
            Node right = nodes.get(current.rightId());
            current = p.features()[current.featureIndex()] <= current.threshold() ? left : right;
        }
    }

    private record Split(int featureIndex, double threshold, double gain) {
    }

    private static Split bestSplit(List<Point> rows, int featureCount, int classCount, int minSamples) {
        Split best = null;
        double parentImpurity = gini(rows, classCount);
        for (int j = 0; j < featureCount; j++) {
            List<Double> values = new ArrayList<>(rows.size());
            for (Point p : rows) {
                values.add(p.features()[j]);
            }
            values.sort(Double::compareTo);
            for (int i = 1; i < values.size(); i++) {
                double low = values.get(i - 1);
                double high = values.get(i);
                if (high - low < EPS) {
                    continue;
                }
                double threshold = (low + high) / 2.0;
                List<Point> left = new ArrayList<>();
                List<Point> right = new ArrayList<>();
                for (Point p : rows) {
                    if (p.features()[j] <= threshold) {
                        left.add(p);
                    } else {
                        right.add(p);
                    }
                }
                if (left.size() < minSamples || right.size() < minSamples) {
                    continue;
                }
                double weighted = (left.size() * gini(left, classCount) + right.size() * gini(right, classCount))
                        / rows.size();
                double gain = parentImpurity - weighted;
                if (best == null || gain > best.gain()) {
                    best = new Split(j, threshold, gain);
                }
            }
        }
        return best;
    }

    private static double gini(List<Point> rows, int classCount) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        int[] counts = new int[classCount];
        for (Point p : rows) {
            int label = p.label() == null ? 0 : p.label();
            if (label >= 0 && label < classCount) {
                counts[label]++;
            }
        }
        double sum = 0.0;
        double n = rows.size();
        for (int c : counts) {
            double prob = c / n;
            sum += prob * prob;
        }
        return 1.0 - sum;
    }

    private static int resolveClassCount(Dataset dataset) {
        if (dataset.classNames() != null && !dataset.classNames().isEmpty()) {
            return dataset.classNames().size();
        }
        int max = 0;
        for (Point p : dataset.points()) {
            if (p.label() != null) {
                max = Math.max(max, p.label());
            }
        }
        return max + 1;
    }

    private static final class NodeBuilder {
        private final int id;
        private final int depth;
        private final List<Point> rows;
        private final double impurity;
        private final int classIndex;
        private Integer featureIndex;
        private Double threshold;
        private Integer leftId;
        private Integer rightId;
        private int splitOrder = -1;

        private NodeBuilder(int id, int depth, List<Point> rows, int classCount) {
            this.id = id;
            this.depth = depth;
            this.rows = rows;
            this.impurity = gini(rows, classCount);
            this.classIndex = majorityLabel(rows, classCount);
        }

        private static int majorityLabel(List<Point> rows, int classCount) {
            int[] counts = new int[classCount];
            for (Point p : rows) {
                int label = p.label() == null ? 0 : p.label();
                if (label >= 0 && label < classCount) {
                    counts[label]++;
                }
            }
            int best = 0;
            for (int c = 1; c < counts.length; c++) {
                if (counts[c] > counts[best]) {
                    best = c;
                }
            }
            return best;
        }
    }

    // ==================== 工具方法 ====================

    private static double requireTarget(Point p) {
        if (p.target() == null) {
            throw new IllegalArgumentException("回归数据集缺少目标值。");
        }
        return p.target();
    }

    private static double dotNormalized(double[] features, double[] means, double[] scales, double[] weights) {
        double sum = 0.0;
        for (int j = 0; j < weights.length; j++) {
            sum += weights[j] * normalize(features[j], means[j], scales[j]);
        }
        return sum;
    }

    private static double normalize(double raw, double mean, double scale) {
        return (raw - mean) / Math.max(EPS, scale);
    }

    private static double denormalize(double normalized, double mean, double scale) {
        return normalized * Math.max(EPS, scale) + mean;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.round(value * 10000.0) / 10000.0;
    }

    /** 供 DTO 转换使用：把节点按 id 建索引。 */
    public static Map<Integer, Node> indexById(List<Node> nodes) {
        Map<Integer, Node> map = new LinkedHashMap<>();
        for (Node node : nodes) {
            map.put(node.id(), node);
        }
        return map;
    }

    /** 供测试使用：读取数据集里所有类别标签。 */
    public static int[] classHistogram(Dataset dataset, int classCount) {
        int[] counts = new int[classCount];
        for (Point p : dataset.points()) {
            if (p.label() != null && p.label() >= 0 && p.label() < classCount) {
                counts[p.label()]++;
            }
        }
        return Arrays.copyOf(counts, counts.length);
    }
}
