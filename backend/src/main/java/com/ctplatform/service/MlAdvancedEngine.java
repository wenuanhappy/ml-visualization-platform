package com.ctplatform.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 其余五个算法的仿真核心（第 08 组新增）。
 *
 * <p>与 {@link MlSimulationEngine}（线性回归、决策树）并列，
 * 同样是不依赖 Spring / 数据库的纯计算类，便于单元测试。</p>
 *
 * <p>语义对齐本组原项目 {@code TrainingSimulationService}：
 * 逻辑回归用交叉熵 + 动量梯度下降，SVM 用合页损失 + L2，K-Means 用 Lloyd 迭代，
 * PCA 用协方差矩阵特征分解，随机森林用自助采样 + 特征随机 + 多数投票。
 * 与原实现的差异同前：不保存模型快照，按步数确定性重算。</p>
 */
public final class MlAdvancedEngine {

    private static final double EPS = 1e-9;
    private static final double MOMENTUM = 0.35;
    private static final double SVM_L2 = 0.01;

    private MlAdvancedEngine() {
    }

    /** 二分类线性模型结果（逻辑回归 / SVM 共用）。 */
    public record ClassificationResult(double[] weights, double bias,
                                       double[] featureMeans,
                                       List<List<Double>> boundaryLine,
                                       List<Integer> predictions,
                                       List<MlSimulationEngine.MetricPoint> history) {
    }

    /** K-Means 结果。 */
    public record ClusterResult(List<List<Double>> centroids, List<Integer> assignments,
                                List<MlSimulationEngine.MetricPoint> history) {
    }

    /** PCA 结果。 */
    public record PcaResult(List<List<Double>> components, List<List<Double>> projections,
                            List<Double> explainedVariance, List<Double> cumulativeVariance) {
    }

    /** 随机森林结果。 */
    public record ForestResult(List<Integer> assignments, List<MlSimulationEngine.MetricPoint> history,
                               double[] importances) {
    }

    // 复用主引擎公开的数据结构，避免两套模型
    private static MlSimulationEngine.MetricPoint metricPoint(int step, double loss, double score) {
        return new MlSimulationEngine.MetricPoint(step, loss, score);
    }

    private record Normalizer(double[] means, double[] scales) {

        double[] normalize(double[] raw) {
            double[] out = new double[raw.length];
            for (int i = 0; i < raw.length; i++) {
                out[i] = (raw[i] - means[i]) / Math.max(EPS, scales[i]);
            }
            return out;
        }

        double denormalize(int index, double normalized) {
            return normalized * Math.max(EPS, scales[index]) + means[index];
        }
    }

    private static Normalizer fit(MlSimulationEngine.Dataset dataset) {
        int featureCount = dataset.featureCount();
        int n = dataset.points().size();
        double[] means = new double[featureCount];
        double[] scales = new double[featureCount];
        for (int j = 0; j < featureCount; j++) {
            double sum = 0.0;
            for (MlSimulationEngine.Point p : dataset.points()) {
                sum += p.features()[j];
            }
            means[j] = sum / n;
            double var = 0.0;
            for (MlSimulationEngine.Point p : dataset.points()) {
                double d = p.features()[j] - means[j];
                var += d * d;
            }
            scales[j] = Math.sqrt(var / n);
        }
        return new Normalizer(means, scales);
    }

    // ==================== 逻辑回归 ====================

    public static ClassificationResult trainLogisticRegression(MlSimulationEngine.Dataset dataset,
                                                               double learningRate, int steps) {
        return trainLinearClassifier(dataset, learningRate, steps, false);
    }

    // ==================== 支持向量机（线性核 + 合页损失） ====================

    public static ClassificationResult trainSupportVectorMachine(MlSimulationEngine.Dataset dataset,
                                                                 double learningRate, int steps) {
        return trainLinearClassifier(dataset, learningRate, steps, true);
    }

    private static ClassificationResult trainLinearClassifier(MlSimulationEngine.Dataset dataset,
                                                              double learningRate, int steps,
                                                              boolean svm) {
        List<MlSimulationEngine.Point> points = dataset.points();
        int featureCount = dataset.featureCount();
        int n = points.size();
        Normalizer normalizer = fit(dataset);

        double lr = svm ? clamp(learningRate, 0.0001, 0.1) : clamp(learningRate, 0.0001, 0.8);
        double[] weights = new double[featureCount];
        double bias = 0.0;
        List<MlSimulationEngine.MetricPoint> history = new ArrayList<>();
        int totalSteps = Math.max(1, steps);

        for (int step = 1; step <= totalSteps; step++) {
            double[] gradient = new double[featureCount];
            double gradientBias = 0.0;
            if (svm) {
                for (MlSimulationEngine.Point p : points) {
                    double[] z = normalizer.normalize(p.features());
                    double sign = label(p) == 1 ? 1.0 : -1.0;
                    double margin = sign * (dot(weights, z) + bias);
                    if (margin < 1.0) {
                        for (int j = 0; j < featureCount; j++) {
                            gradient[j] -= sign * z[j];
                        }
                        gradientBias -= sign;
                    }
                }
                for (int j = 0; j < featureCount; j++) {
                    gradient[j] = gradient[j] / n + SVM_L2 * weights[j];
                }
                gradientBias = gradientBias / n;
            } else {
                for (MlSimulationEngine.Point p : points) {
                    double[] z = normalizer.normalize(p.features());
                    double probability = sigmoid(dot(weights, z) + bias);
                    double error = probability - label(p);
                    for (int j = 0; j < featureCount; j++) {
                        gradient[j] += error * z[j];
                    }
                    gradientBias += error;
                }
                for (int j = 0; j < featureCount; j++) {
                    gradient[j] /= n;
                }
                gradientBias /= n;
            }

            for (int j = 0; j < featureCount; j++) {
                double target = weights[j] - lr * gradient[j];
                weights[j] = weights[j] + (target - weights[j]) * MOMENTUM;
            }
            double targetBias = bias - lr * gradientBias;
            bias = bias + (targetBias - bias) * MOMENTUM;

            history.add(metric(points, normalizer, weights, bias, step, svm));
        }

        List<Integer> predictions = new ArrayList<>(n);
        for (MlSimulationEngine.Point p : points) {
            predictions.add(dot(weights, normalizer.normalize(p.features())) + bias >= 0 ? 1 : 0);
        }
        // 换算回原始特征空间：w_raw = w / scale，b_raw = b - Σ w_raw * mean
        double[] rawWeights = new double[featureCount];
        double rawBias = bias;
        for (int j = 0; j < featureCount; j++) {
            rawWeights[j] = weights[j] / Math.max(EPS, normalizer.scales()[j]);
            rawBias -= rawWeights[j] * normalizer.means()[j];
        }
        List<List<Double>> boundary = boundaryLine(dataset, rawWeights, rawBias);
        return new ClassificationResult(rawWeights, round(rawBias), normalizer.means().clone(),
                boundary, predictions, history);
    }

    private static MlSimulationEngine.MetricPoint metric(List<MlSimulationEngine.Point> points, Normalizer normalizer,
                                      double[] weights, double bias, int step, boolean svm) {
        double loss = 0.0;
        int correct = 0;
        for (MlSimulationEngine.Point p : points) {
            double z = dot(weights, normalizer.normalize(p.features())) + bias;
            int predicted = z >= 0 ? 1 : 0;
            int actual = label(p);
            if (predicted == actual) {
                correct++;
            }
            if (svm) {
                double sign = actual == 1 ? 1.0 : -1.0;
                loss += Math.max(0.0, 1.0 - sign * z);
            } else {
                double probability = clamp(sigmoid(z), EPS, 1.0 - EPS);
                loss += -(actual * Math.log(probability) + (1 - actual) * Math.log(1 - probability));
            }
        }
        double n = Math.max(1, points.size());
        return metricPoint(step, round(loss / n), round((double) correct / n));
    }

    /**
     * 决策边界：在归一化空间里解 w·z + b = 0，再映射回原始坐标。
     * 只对二维特征生成线段，维度更高时返回 null（前端不画线）。
     */
    private static List<List<Double>> boundaryLine(MlSimulationEngine.Dataset dataset,
                                                   double[] weights, double bias) {
        if (dataset.featureCount() != 2) {
            return null;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (MlSimulationEngine.Point p : dataset.points()) {
            minX = Math.min(minX, p.features()[0]);
            maxX = Math.max(maxX, p.features()[0]);
            minY = Math.min(minY, p.features()[1]);
            maxY = Math.max(maxY, p.features()[1]);
        }
        if (Math.abs(weights[1]) < 1e-6) {
            // 垂直于 x 轴的边界
            double x = Math.abs(weights[0]) < 1e-6 ? 0.0 : -bias / weights[0];
            return List.of(List.of(round(x), round(minY)), List.of(round(x), round(maxY)));
        }
        List<List<Double>> line = new ArrayList<>(2);
        for (double x : new double[]{minX, maxX}) {
            double y = -(weights[0] * x + bias) / weights[1];
            line.add(List.of(round(x), round(y)));
        }
        return line;
    }

    // ==================== K-Means ====================

    /**
     * K-Means：逐行对照原项目 TrainingSimulationService.trainKMeans。
     *
     * 与原写法保持一致的三处关键点：
     * 1. 初始质心不是 k-means++，而是沿每个维度的取值范围均匀铺开
     *    （position = (i+1)/(k+1)，奇数维再加一点错位），因此每步结果可复现；
     * 2. 距离在**按维度归一化之后**计算，避免量级大的特征主导；
     * 3. 质心更新带 0.35 动量，逐步靠近簇均值而不是一步跳过去。
     */
    public static ClusterResult trainKMeans(MlSimulationEngine.Dataset dataset, int k, int steps) {
        List<MlSimulationEngine.Point> points = dataset.points();
        int featureCount = dataset.featureCount();
        int clusterCount = Math.max(1, Math.min(k, Math.max(1, points.size())));
        Normalizer normalizer = fit(dataset);

        double[] mins = new double[featureCount];
        double[] ranges = new double[featureCount];
        for (int j = 0; j < featureCount; j++) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (MlSimulationEngine.Point p : points) {
                min = Math.min(min, p.features()[j]);
                max = Math.max(max, p.features()[j]);
            }
            mins[j] = min;
            ranges[j] = Math.max(EPS, max - min);
        }

        // 初始质心：沿取值范围均匀铺开（原项目 initializeCentroids）
        List<double[]> centroids = new ArrayList<>();
        for (int i = 0; i < clusterCount; i++) {
            double[] values = new double[featureCount];
            for (int j = 0; j < featureCount; j++) {
                double position = (i + 1.0) / (clusterCount + 1.0);
                double jitter = j % 2 == 0 ? 0.0 : (((i % 2) + 1) / 3.0 - 0.5) * 0.6;
                values[j] = mins[j] + ranges[j] * clamp(position + jitter, 0.05, 0.95);
            }
            centroids.add(values);
        }

        List<MlSimulationEngine.MetricPoint> history = new ArrayList<>();
        List<Integer> assignments = new ArrayList<>();
        int totalSteps = Math.max(1, steps);

        for (int step = 1; step <= totalSteps; step++) {
            assignments = assignNormalized(points, centroids, normalizer);
            centroids = recomputeWithMomentum(points, assignments, centroids, featureCount, 0.35);
            double inertia = 0.0;
            List<Integer> finalAssignments = assignNormalized(points, centroids, normalizer);
            for (int i = 0; i < points.size(); i++) {
                inertia += normalizedSquaredDistance(points.get(i).features(),
                        centroids.get(finalAssignments.get(i)), normalizer);
            }
            double normalizedInertia = inertia / Math.max(EPS, points.size() * Math.max(1, featureCount) * 4.0);
            double silhouette = approximateSilhouette(points, finalAssignments, centroids, normalizer);
            assignments = finalAssignments;
            history.add(metricPoint(step, round(normalizedInertia), round(silhouette)));
        }

        List<List<Double>> centroidDtos = new ArrayList<>();
        for (double[] centroid : centroids) {
            List<Double> row = new ArrayList<>(centroid.length);
            for (double value : centroid) {
                row.add(round(value));
            }
            centroidDtos.add(row);
        }
        return new ClusterResult(centroidDtos, assignments, history);
    }

    /** 归一化空间里的平方距离（原项目 normalizedSquaredDistance） */
    private static double normalizedSquaredDistance(double[] features, double[] centroid, Normalizer normalizer) {
        double total = 0.0;
        for (int j = 0; j < Math.min(centroid.length, features.length); j++) {
            double diff = normalizer.normalize(features)[j] - normalizer.normalize(centroid)[j];
            total += diff * diff;
        }
        return total;
    }

    private static List<Integer> assignNormalized(List<MlSimulationEngine.Point> points,
                                                  List<double[]> centroids, Normalizer normalizer) {
        List<Integer> assignments = new ArrayList<>(points.size());
        for (MlSimulationEngine.Point p : points) {
            int best = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int c = 0; c < centroids.size(); c++) {
                double distance = normalizedSquaredDistance(p.features(), centroids.get(c), normalizer);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = c;
                }
            }
            assignments.add(best);
        }
        return assignments;
    }

    /** 质心向簇均值移动，但只走 0.35（原项目 recomputeCentroids 的动量） */
    private static List<double[]> recomputeWithMomentum(List<MlSimulationEngine.Point> points,
                                                        List<Integer> assignments,
                                                        List<double[]> previous,
                                                        int featureCount,
                                                        double momentum) {
        List<double[]> updated = new ArrayList<>(previous.size());
        for (int clusterId = 0; clusterId < previous.size(); clusterId++) {
            double[] sums = new double[featureCount];
            int count = 0;
            for (int i = 0; i < points.size(); i++) {
                if (assignments.get(i) != clusterId) {
                    continue;
                }
                for (int j = 0; j < featureCount; j++) {
                    sums[j] += points.get(i).features()[j];
                }
                count++;
            }
            double[] values = new double[featureCount];
            for (int j = 0; j < featureCount; j++) {
                double mean = count == 0 ? previous.get(clusterId)[j] : sums[j] / count;
                values[j] = previous.get(clusterId)[j] + (mean - previous.get(clusterId)[j]) * momentum;
            }
            updated.add(values);
        }
        return updated;
    }

    /** 原项目的轮廓系数近似：(平均 (b-a)/max(a,b) + 1) / 2，截到 0.99 */
    private static double approximateSilhouette(List<MlSimulationEngine.Point> points,
                                                List<Integer> assignments, List<double[]> centroids,
                                                Normalizer normalizer) {
        if (centroids.size() <= 1 || points.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < points.size(); i++) {
            int own = assignments.get(i);
            double a = Math.sqrt(normalizedSquaredDistance(points.get(i).features(), centroids.get(own), normalizer));
            double b = Double.MAX_VALUE;
            for (int c = 0; c < centroids.size(); c++) {
                if (c == own) {
                    continue;
                }
                b = Math.min(b, Math.sqrt(normalizedSquaredDistance(points.get(i).features(), centroids.get(c), normalizer)));
            }
            total += (b - a) / Math.max(a, b);
        }
        return clamp((total / points.size() + 1.0) / 2.0, 0.0, 0.99);
    }





    /** 轮廓系数（O(n²)，样本量百级时足够）。 */

    // ==================== PCA ====================

    /**
     * 标准化后做协方差矩阵特征分解，返回前 k 个主成分与投影坐标。
     */
    public static PcaResult computePca(MlSimulationEngine.Dataset dataset, int components) {
        List<MlSimulationEngine.Point> points = dataset.points();
        int featureCount = dataset.featureCount();
        int k = Math.max(1, Math.min(components, featureCount));
        Normalizer normalizer = fit(dataset);

        double[][] z = new double[points.size()][featureCount];
        for (int i = 0; i < points.size(); i++) {
            z[i] = normalizer.normalize(points.get(i).features());
        }

        double[][] covariance = new double[featureCount][featureCount];
        for (double[] row : z) {
            for (int a = 0; a < featureCount; a++) {
                for (int b = 0; b < featureCount; b++) {
                    covariance[a][b] += row[a] * row[b];
                }
            }
        }
        for (int a = 0; a < featureCount; a++) {
            for (int b = 0; b < featureCount; b++) {
                covariance[a][b] /= Math.max(1, points.size() - 1);
            }
        }

        double[][] working = new double[featureCount][];
        for (int i = 0; i < featureCount; i++) {
            working[i] = covariance[i].clone();
        }
        double[][] eigenvectors = jacobiEigen(working, featureCount);
        // 特征值 = 投影后的方差，按定义直接算，避免依赖 Jacobi 的对角元
        double[] eigenvalues = new double[featureCount];
        for (int c = 0; c < featureCount; c++) {
            double sum = 0.0;
            for (double[] row : z) {
                double projection = 0.0;
                for (int j = 0; j < featureCount; j++) {
                    projection += row[j] * eigenvectors[j][c];
                }
                sum += projection * projection;
            }
            eigenvalues[c] = sum / Math.max(1, points.size() - 1);
        }

        Integer[] order = new Integer[featureCount];
        for (int i = 0; i < featureCount; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer index) -> -eigenvalues[index]));

        double totalVariance = 0.0;
        for (double value : eigenvalues) {
            totalVariance += value;
        }

        List<List<Double>> componentDtos = new ArrayList<>();
        List<Double> explained = new ArrayList<>();
        List<Double> cumulative = new ArrayList<>();
        double accumulated = 0.0;
        for (int c = 0; c < k; c++) {
            int index = order[c];
            List<Double> component = new ArrayList<>();
            for (int j = 0; j < featureCount; j++) {
                component.add(round(eigenvectors[j][index]));
            }
            componentDtos.add(component);
            double ratio = totalVariance < EPS ? 0.0 : eigenvalues[index] / totalVariance;
            accumulated += ratio;
            explained.add(round(ratio));
            cumulative.add(round(Math.min(1.0, accumulated)));
        }

        List<List<Double>> projections = new ArrayList<>();
        for (double[] row : z) {
            List<Double> projected = new ArrayList<>();
            for (int c = 0; c < k; c++) {
                int index = order[c];
                double value = 0.0;
                for (int j = 0; j < featureCount; j++) {
                    value += row[j] * eigenvectors[j][index];
                }
                projected.add(round(value));
            }
            projections.add(projected);
        }
        return new PcaResult(componentDtos, projections, explained, cumulative);
    }

    /**
     * 循环 Jacobi 特征分解，返回按列存放的特征向量。
     */
    private static double[][] jacobiEigen(double[][] matrix, int n) {
        double[][] vectors = new double[n][n];
        for (int i = 0; i < n; i++) {
            vectors[i][i] = 1.0;
        }
        for (int sweep = 0; sweep < 60; sweep++) {
            double offDiagonal = 0.0;
            for (int p = 0; p < n; p++) {
                for (int q = p + 1; q < n; q++) {
                    offDiagonal += matrix[p][q] * matrix[p][q];
                }
            }
            if (offDiagonal < 1e-18) {
                break;
            }
            for (int p = 0; p < n; p++) {
                for (int q = p + 1; q < n; q++) {
                    if (Math.abs(matrix[p][q]) < 1e-15) {
                        continue;
                    }
                    double theta = (matrix[q][q] - matrix[p][p]) / (2.0 * matrix[p][q]);
                    double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1.0));
                    if (theta == 0.0) {
                        t = 1.0;
                    }
                    double c = 1.0 / Math.sqrt(t * t + 1.0);
                    double s = t * c;
                    for (int r = 0; r < n; r++) {
                        double mrP = matrix[r][p];
                        double mrQ = matrix[r][q];
                        matrix[r][p] = c * mrP - s * mrQ;
                        matrix[r][q] = s * mrP + c * mrQ;
                    }
                    for (int r = 0; r < n; r++) {
                        double mpR = matrix[p][r];
                        double mqR = matrix[q][r];
                        matrix[p][r] = c * mpR - s * mqR;
                        matrix[q][r] = s * mpR + c * mqR;
                    }
                    for (int r = 0; r < n; r++) {
                        double vrP = vectors[r][p];
                        double vrQ = vectors[r][q];
                        vectors[r][p] = c * vrP - s * vrQ;
                        vectors[r][q] = s * vrP + c * vrQ;
                    }
                }
            }
        }
        return vectors;
    }

    // ==================== 随机森林 ====================

    /**
     * 一次性训练 {@code treeCount} 棵树，第 t 步的指标是「只用前 t 棵树的投票结果」。
     */
    public static ForestResult trainRandomForest(MlSimulationEngine.Dataset dataset, int treeCount,
                                                 int maxDepth, int steps) {
        List<MlSimulationEngine.Point> points = dataset.points();
        int classCount = classCount(dataset);
        int featureCount = dataset.featureCount();
        int mtry = Math.max(1, (int) Math.round(Math.sqrt(featureCount)));
        int totalSteps = Math.max(1, Math.min(steps, treeCount));
        Random random = new Random(2026L);

        double[] importances = new double[featureCount];
        List<RfNode> forest = new ArrayList<>();
        List<MlSimulationEngine.MetricPoint> history = new ArrayList<>();
        List<Integer> assignments = new ArrayList<>();

        for (int t = 1; t <= totalSteps; t++) {
            List<MlSimulationEngine.Point> bootstrap = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                bootstrap.add(points.get(random.nextInt(points.size())));
            }
            int[] features = new int[Math.min(mtry, featureCount)];
            List<Integer> pool = new ArrayList<>();
            for (int j = 0; j < featureCount; j++) {
                pool.add(j);
            }
            for (int i = 0; i < features.length; i++) {
                features[i] = pool.remove(random.nextInt(pool.size()));
            }
            forest.add(grow(bootstrap, features, 0, maxDepth, classCount, random, importances));

            assignments = vote(forest, points, classCount);
            int correct = 0;
            for (int i = 0; i < points.size(); i++) {
                if (assignments.get(i).equals(points.get(i).label())) {
                    correct++;
                }
            }
            double accuracy = points.isEmpty() ? 0.0 : (double) correct / points.size();
            double impurity = 1.0 - accuracy;
            history.add(metricPoint(t, round(impurity), round(accuracy)));
        }
        return new ForestResult(assignments, history, importances);
    }

    private static final class RfNode {
        private int featureIndex = -1;
        private double threshold;
        private int classIndex;
        private RfNode left;
        private RfNode right;

        private boolean isLeaf() {
            return featureIndex < 0;
        }
    }

    private static RfNode grow(List<MlSimulationEngine.Point> rows, int[] features, int depth,
                               int maxDepth, int classCount, Random random, double[] importances) {
        RfNode node = new RfNode();
        node.classIndex = majority(rows, classCount);
        if (depth >= maxDepth || rows.size() < 4 || gini(rows, classCount) < 1e-6) {
            return node;
        }
        double bestGain = 0.0;
        int bestFeature = -1;
        double bestThreshold = 0.0;
        double parent = gini(rows, classCount);
        for (int feature : features) {
            List<Double> values = new ArrayList<>(rows.size());
            for (MlSimulationEngine.Point p : rows) {
                values.add(p.features()[feature]);
            }
            values.sort(Double::compareTo);
            for (int i = 1; i < values.size(); i++) {
                double low = values.get(i - 1);
                double high = values.get(i);
                if (high - low < EPS) {
                    continue;
                }
                double threshold = (low + high) / 2.0;
                List<MlSimulationEngine.Point> left = new ArrayList<>();
                List<MlSimulationEngine.Point> right = new ArrayList<>();
                for (MlSimulationEngine.Point p : rows) {
                    if (p.features()[feature] <= threshold) {
                        left.add(p);
                    } else {
                        right.add(p);
                    }
                }
                if (left.isEmpty() || right.isEmpty()) {
                    continue;
                }
                double weighted = (left.size() * gini(left, classCount) + right.size() * gini(right, classCount))
                        / rows.size();
                double gain = parent - weighted;
                if (gain > bestGain) {
                    bestGain = gain;
                    bestFeature = feature;
                    bestThreshold = threshold;
                }
            }
        }
        if (bestFeature < 0 || bestGain < 1e-9) {
            return node;
        }
        node.featureIndex = bestFeature;
        node.threshold = bestThreshold;
        List<MlSimulationEngine.Point> leftRows = new ArrayList<>();
        List<MlSimulationEngine.Point> rightRows = new ArrayList<>();
        for (MlSimulationEngine.Point p : rows) {
            if (p.features()[bestFeature] <= bestThreshold) {
                leftRows.add(p);
            } else {
                rightRows.add(p);
            }
        }
        importances[bestFeature] += bestGain * rows.size();
        node.left = grow(leftRows, features, depth + 1, maxDepth, classCount, random, importances);
        node.right = grow(rightRows, features, depth + 1, maxDepth, classCount, random, importances);
        return node;
    }

    private static List<Integer> vote(List<RfNode> forest, List<MlSimulationEngine.Point> points, int classCount) {
        List<Integer> assignments = new ArrayList<>(points.size());
        for (MlSimulationEngine.Point p : points) {
            int[] votes = new int[classCount];
            for (RfNode tree : forest) {
                votes[predict(tree, p)]++;
            }
            int best = 0;
            for (int c = 1; c < classCount; c++) {
                if (votes[c] > votes[best]) {
                    best = c;
                }
            }
            assignments.add(best);
        }
        return assignments;
    }

    private static int predict(RfNode node, MlSimulationEngine.Point p) {
        RfNode current = node;
        while (!current.isLeaf()) {
            current = p.features()[current.featureIndex] <= current.threshold ? current.left : current.right;
        }
        return current.classIndex;
    }

    private static int majority(List<MlSimulationEngine.Point> rows, int classCount) {
        int[] counts = new int[classCount];
        for (MlSimulationEngine.Point p : rows) {
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

    private static double gini(List<MlSimulationEngine.Point> rows, int classCount) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        int[] counts = new int[classCount];
        for (MlSimulationEngine.Point p : rows) {
            int label = p.label() == null ? 0 : p.label();
            if (label >= 0 && label < classCount) {
                counts[label]++;
            }
        }
        double sum = 0.0;
        for (int c : counts) {
            double probability = (double) c / rows.size();
            sum += probability * probability;
        }
        return 1.0 - sum;
    }

    private static int classCount(MlSimulationEngine.Dataset dataset) {
        if (dataset.classNames() != null && !dataset.classNames().isEmpty()) {
            return dataset.classNames().size();
        }
        int max = 0;
        for (MlSimulationEngine.Point p : dataset.points()) {
            if (p.label() != null) {
                max = Math.max(max, p.label());
            }
        }
        return max + 1;
    }

    // ==================== 工具方法 ====================

    private static int label(MlSimulationEngine.Point p) {
        if (p.label() == null) {
            return 0;
        }
        return p.label() >= 1 ? 1 : 0;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static double squaredDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
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
}
