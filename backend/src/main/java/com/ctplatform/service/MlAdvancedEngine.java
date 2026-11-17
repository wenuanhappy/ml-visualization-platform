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
                                       List<List<List<Double>>> boundaryLines,
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
        List<List<List<Double>>> boundaryLines = boundary == null
                ? List.of()
                : List.of(boundary);
        return new ClassificationResult(rawWeights, round(rawBias), normalizer.means().clone(),
                boundary, boundaryLines, predictions, history);
    }

    // ==================== 非线性核 SVM（poly / rbf） ====================

    /**
     * 核方法 SVM：用核技巧在原始两维特征上做合页损失 SGD，再把决策边界采样成折线。
     * 原项目 poly/rbf 用的是固定的两维近似权重；这里用等价的标准核方法实现，
     * 保证界面上 poly / rbf 两个核函数都能得到真正的非线性边界。
     */
    public static ClassificationResult trainKernelSvm(MlSimulationEngine.Dataset dataset, String kernel,
                                                      double gamma, int steps) {
        List<MlSimulationEngine.Point> points = dataset.points();
        int featureCount = Math.min(2, dataset.featureCount());
        int n = points.size();
        Normalizer normalizer = fit(dataset);

        double[][] z = new double[n][featureCount];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double[] normalized = normalizer.normalize(points.get(i).features());
            for (int j = 0; j < featureCount; j++) {
                z[i][j] = normalized[j];
            }
            y[i] = label(points.get(i)) == 1 ? 1.0 : -1.0;
        }
        double g = Math.max(0.001, gamma);

        double[] alpha = new double[n];
        double bias = 0.0;
        int totalSteps = Math.max(1, steps);
        List<MlSimulationEngine.MetricPoint> history = new ArrayList<>();

        for (int step = 1; step <= totalSteps; step++) {
            // 一个"步"里做多轮扫描，否则 60 步根本收敛不到有意义的边界
            double learningRate = 0.8 / (1.0 + step * 0.08);
            for (int epoch = 0; epoch < 8; epoch++) {
            for (int i = 0; i < n; i++) {
                double score = bias;
                for (int j = 0; j < n; j++) {
                    if (alpha[j] == 0.0) {
                        continue;
                    }
                    score += alpha[j] * y[j] * kernelValue(kernel, g, z[i], z[j]);
                }
                if (y[i] * score < 1.0) {
                    alpha[i] += learningRate;
                    bias += learningRate * y[i];
                }
            }
            }
            int correct = 0;
            for (int i = 0; i < n; i++) {
                double score = bias;
                for (int j = 0; j < n; j++) {
                    if (alpha[j] == 0.0) {
                        continue;
                    }
                    score += alpha[j] * y[j] * kernelValue(kernel, g, z[i], z[j]);
                }
                if ((score >= 0 ? 1 : 0) == label(points.get(i))) {
                    correct++;
                }
            }
            double accuracy = n == 0 ? 0.0 : (double) correct / n;
            history.add(metricPoint(step, round(1.0 - accuracy), round(accuracy)));
        }

        List<Integer> predictions = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double score = bias;
            for (int j = 0; j < n; j++) {
                if (alpha[j] == 0.0) {
                    continue;
                }
                score += alpha[j] * y[j] * kernelValue(kernel, g, z[i], z[j]);
            }
            predictions.add(score >= 0 ? 1 : 0);
        }

        // 采样出边界折线：把碎段按端点相连拼成折线，取最长的一条
        List<List<List<Double>>> boundaryLines = sampleKernelBoundaries(points, normalizer, featureCount,
                kernel, g, alpha, y, z, bias);
        List<List<Double>> boundary = boundaryLines.isEmpty()
                ? List.of()
                : boundaryLines.get(0);

        return new ClassificationResult(new double[]{0.0}, 0.0, normalizer.means().clone(),
                boundary.isEmpty() ? null : boundary, boundaryLines, predictions, history);
    }

    private static double kernelValue(String kernel, double gamma, double[] a, double[] b) {
        if ("rbf".equals(kernel)) {
            double sum = 0.0;
            for (int j = 0; j < a.length; j++) {
                double diff = a[j] - b[j];
                sum += diff * diff;
            }
            return Math.exp(-gamma * sum);
        }
        // poly：degree = 2、coef0 = 1
        double dot = 0.0;
        for (int j = 0; j < a.length; j++) {
            dot += a[j] * b[j];
        }
        return Math.pow(gamma * dot + 1.0, 2);
    }

    /** 网格 + marching squares，把核 SVM 的 0 等值线采样成分段折线 */
    private static List<List<List<Double>>> sampleKernelBoundaries(List<MlSimulationEngine.Point> points,
                                                           Normalizer normalizer, int featureCount,
                                                           String kernel, double gamma,
                                                           double[] alpha, double[] y, double[][] z,
                                                           double bias) {
        List<List<List<Double>>> chains = new ArrayList<>();
        if (points.isEmpty() || featureCount < 2) {
            return chains;
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (MlSimulationEngine.Point p : points) {
            minX = Math.min(minX, p.features()[0]);
            maxX = Math.max(maxX, p.features()[0]);
            minY = Math.min(minY, p.features()[1]);
            maxY = Math.max(maxY, p.features()[1]);
        }
        double padX = (maxX - minX) * 0.05 + 1e-6;
        double padY = (maxY - minY) * 0.05 + 1e-6;
        minX -= padX; maxX += padX; minY -= padY; maxY += padY;

        int grid = 40;
        double[][] values = new double[grid + 1][grid + 1];
        for (int i = 0; i <= grid; i++) {
            for (int j = 0; j <= grid; j++) {
                double rawX = minX + (maxX - minX) * i / grid;
                double rawY = minY + (maxY - minY) * j / grid;
                double[] probe = new double[featureCount];
                probe[0] = (rawX - normalizer.means()[0]) / Math.max(EPS, normalizer.scales()[0]);
                probe[1] = (rawY - normalizer.means()[1]) / Math.max(EPS, normalizer.scales()[1]);
                double score = bias;
                for (int k = 0; k < alpha.length; k++) {
                    if (alpha[k] == 0.0) {
                        continue;
                    }
                    score += alpha[k] * y[k] * kernelValue(kernel, gamma, probe, z[k]);
                }
                values[i][j] = score;
            }
        }

        List<List<double[]>> rawSegments = new ArrayList<>();
        // 每个单元里找符号变化的边，插值出交点，连成一条短线段
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                double v00 = values[i][j];
                double v10 = values[i + 1][j];
                double v01 = values[i][j + 1];
                double v11 = values[i + 1][j + 1];
                List<double[]> crossings = new ArrayList<>(4);
                addCrossing(crossings, v00, v10, i, j, i + 1, j, minX, maxX, minY, maxY, grid);
                addCrossing(crossings, v10, v11, i + 1, j, i + 1, j + 1, minX, maxX, minY, maxY, grid);
                addCrossing(crossings, v11, v01, i + 1, j + 1, i, j + 1, minX, maxX, minY, maxY, grid);
                addCrossing(crossings, v01, v00, i, j + 1, i, j, minX, maxX, minY, maxY, grid);
                for (int a = 0; a + 1 < crossings.size(); a += 2) {
                    rawSegments.add(List.of(crossings.get(a), crossings.get(a + 1)));
                }
            }
        }
        // 直接返回每一小段：链式拼接在闭合曲线处容易断成几截，导致画布只画出半条线；
        // 画布对每条折线都画成虚线，短段拼起来就是完整曲线
        List<List<List<Double>>> segmentsOut = new ArrayList<>(rawSegments.size());
        for (List<double[]> segment : rawSegments) {
            List<List<Double>> line = new ArrayList<>(2);
            for (double[] point : segment) {
                line.add(List.of(round(point[0]), round(point[1])));
            }
            segmentsOut.add(line);
        }
        return segmentsOut;
    }

    /** 把碎线段按端点距离贪心串成多条折线，全部返回（画布支持多段） */
    private static List<List<List<Double>>> chainAll(List<List<double[]>> segments, double step) {
        double tolerance = Math.max(step * 1.8, 1e-9);
        List<List<double[]>> remaining = new ArrayList<>(segments);
        List<List<double[]>> chains = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<double[]> chain = new ArrayList<>(remaining.remove(0));
            boolean extended = true;
            while (extended) {
                extended = false;
                for (int i = 0; i < remaining.size(); i++) {
                    List<double[]> seg = remaining.get(i);
                    double[] head = chain.get(0);
                    double[] tail = chain.get(chain.size() - 1);
                    if (dist(head, seg.get(1)) <= tolerance) {
                        chain.add(0, seg.get(0));
                    } else if (dist(tail, seg.get(0)) <= tolerance) {
                        chain.add(seg.get(1));
                    } else if (dist(tail, seg.get(1)) <= tolerance) {
                        chain.add(seg.get(0));
                    } else if (dist(head, seg.get(0)) <= tolerance) {
                        chain.add(0, seg.get(1));
                    } else {
                        continue;
                    }
                    remaining.remove(i);
                    extended = true;
                    break;
                }
            }
            chains.add(chain);
        }
        List<List<List<Double>>> result = new ArrayList<>();
        // 过短的碎段是网格采样的毛刺，丢掉；保留主曲线与足够长的分支
        for (List<double[]> chain : chains) {
            if (chain.size() < 6) {
                continue;
            }
            List<List<Double>> line = new ArrayList<>(chain.size());
            for (double[] point : chain) {
                line.add(List.of(round(point[0]), round(point[1])));
            }
            result.add(line);
        }
        return result;
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static void addCrossing(List<double[]> out, double va, double vb,
                                    int ia, int ja, int ib, int jb,
                                    double minX, double maxX, double minY, double maxY, int grid) {
        if ((va > 0) == (vb > 0)) {
            return;
        }
        double t = Math.abs(va - vb) < 1e-12 ? 0.5 : va / (va - vb);
        double x = minX + (maxX - minX) * (ia + (ib - ia) * t) / grid;
        double y = minY + (maxY - minY) * (ja + (jb - ja) * t) / grid;
        out.add(new double[]{x, y});
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
     * PCA：逐行对照原项目 TrainingSimulationService.computePca。
     *
     * 与原写法的差异（本项目统一约定）：先按维度做 z-score 标准化再求协方差，
     * 与线性回归 / 逻辑回归 / SVM / K-Means 的归一化口径保持一致；
     * 否则量纲大的特征（如房产税率 300~700）会独占 PC1。
     *
     * 其余与原文一致：
     * 1. 标准化后求协方差；
     * 2. 协方差矩阵按 n-1 归一，总方差取迹；
     * 3. 用幂迭代 + 逐次收缩（deflation）求前 k 个特征向量，初值确定、不引入随机；
     * 4. 每个特征向量做符号归一：绝对值最大的载荷取正，保证载荷矩阵稳定可读。
     */
    public static PcaResult computePca(MlSimulationEngine.Dataset dataset, int components) {
        List<MlSimulationEngine.Point> points = dataset.points();
        int featureCount = dataset.featureCount();
        int k = Math.max(1, Math.min(components, featureCount));
        int n = points.size();

        // 与其它算法同一套归一化：按维度 z-score（均值 0、标准差 1）
        Normalizer normalizer = fit(dataset);
        double[][] centered = new double[n][featureCount];
        for (int i = 0; i < n; i++) {
            centered[i] = normalizer.normalize(points.get(i).features());
        }

        double[][] cov = new double[featureCount][featureCount];
        for (int i = 0; i < featureCount; i++) {
            for (int j = i; j < featureCount; j++) {
                double sum = 0.0;
                for (int r = 0; r < n; r++) {
                    sum += centered[r][i] * centered[r][j];
                }
                double value = n > 1 ? sum / (n - 1) : 0.0;
                cov[i][j] = value;
                cov[j][i] = value;
            }
        }

        double totalVariance = 0.0;
        for (int i = 0; i < featureCount; i++) {
            totalVariance += cov[i][i];
        }

        double[][] working = new double[featureCount][];
        for (int i = 0; i < featureCount; i++) {
            working[i] = cov[i].clone();
        }

        List<List<Double>> componentDtos = new ArrayList<>();
        List<Double> explained = new ArrayList<>();
        List<Double> cumulative = new ArrayList<>();
        double[] eigenvalues = new double[k];
        double[][] vectors = new double[k][featureCount];

        for (int comp = 0; comp < k; comp++) {
            double[] v = new double[featureCount];
            for (int i = 0; i < featureCount; i++) {
                v[i] = 1.0 + 0.1 * (i + 1);   // 确定性初值
            }
            double initNorm = 0.0;
            for (int i = 0; i < featureCount; i++) {
                initNorm += v[i] * v[i];
            }
            initNorm = Math.sqrt(Math.max(1e-12, initNorm));
            for (int i = 0; i < featureCount; i++) {
                v[i] /= initNorm;
            }

            // 幂迭代
            for (int it = 0; it < 200; it++) {
                double[] next = new double[featureCount];
                for (int i = 0; i < featureCount; i++) {
                    double sum = 0.0;
                    for (int j = 0; j < featureCount; j++) {
                        sum += working[i][j] * v[j];
                    }
                    next[i] = sum;
                }
                double norm = 0.0;
                for (int i = 0; i < featureCount; i++) {
                    norm += next[i] * next[i];
                }
                norm = Math.sqrt(Math.max(1e-12, norm));
                for (int i = 0; i < featureCount; i++) {
                    v[i] = next[i] / norm;
                }
            }

            // 符号归一：绝对值最大的载荷取正
            int maxIndex = 0;
            for (int i = 1; i < featureCount; i++) {
                if (Math.abs(v[i]) > Math.abs(v[maxIndex])) {
                    maxIndex = i;
                }
            }
            if (v[maxIndex] < 0) {
                for (int i = 0; i < featureCount; i++) {
                    v[i] = -v[i];
                }
            }

            // 特征值 = vᵀ C v
            double[] cv = new double[featureCount];
            for (int i = 0; i < featureCount; i++) {
                double sum = 0.0;
                for (int j = 0; j < featureCount; j++) {
                    sum += working[i][j] * v[j];
                }
                cv[i] = sum;
            }
            double eigenvalue = 0.0;
            for (int i = 0; i < featureCount; i++) {
                eigenvalue += v[i] * cv[i];
            }
            eigenvalues[comp] = eigenvalue;
            vectors[comp] = v;

            // 收缩：C ← C - λ v vᵀ
            for (int i = 0; i < featureCount; i++) {
                for (int j = 0; j < featureCount; j++) {
                    working[i][j] -= eigenvalue * v[i] * v[j];
                }
            }
        }

        double accumulated = 0.0;
        for (int comp = 0; comp < k; comp++) {
            List<Double> component = new ArrayList<>(featureCount);
            for (int j = 0; j < featureCount; j++) {
                component.add(round(vectors[comp][j]));
            }
            componentDtos.add(component);
            double ratio = totalVariance < EPS ? 0.0 : eigenvalues[comp] / totalVariance;
            accumulated += ratio;
            explained.add(round(ratio));
            cumulative.add(round(Math.min(1.0, accumulated)));
        }

        List<List<Double>> projections = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<Double> projected = new ArrayList<>(k);
            for (int comp = 0; comp < k; comp++) {
                double value = 0.0;
                for (int j = 0; j < featureCount; j++) {
                    value += centered[i][j] * vectors[comp][j];
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
