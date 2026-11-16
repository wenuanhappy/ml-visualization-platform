package com.ctplatform.service;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 其余五个算法的单元测试（第 08 组新增）。
 */
class MlAdvancedEngineTest {

    @Test
    void logisticRegressionShouldSeparateTwoBlobs() {
        MlSimulationEngine.Dataset dataset = twoClassBlobs();
        MlAdvancedEngine.ClassificationResult result =
                MlAdvancedEngine.trainLogisticRegression(dataset, 0.1, 60);

        assertTrue(result.history().get(59).score() > 0.95,
                "准确率应大于 0.95，实际 " + result.history().get(59).score());
        assertNotNull(result.boundaryLine(), "二维数据应给出决策边界");
        assertEquals(2, result.boundaryLine().size());
        // 交叉熵损失应单调下降
        for (int i = 1; i < result.history().size(); i++) {
            assertTrue(result.history().get(i).loss() <= result.history().get(i - 1).loss() + 1e-6);
        }
    }

    @Test
    void svmShouldSeparateTwoBlobs() {
        MlSimulationEngine.Dataset dataset = twoClassBlobs();
        MlAdvancedEngine.ClassificationResult result =
                MlAdvancedEngine.trainSupportVectorMachine(dataset, 0.01, 60);
        assertTrue(result.history().get(59).score() > 0.9,
                "SVM 准确率应大于 0.9，实际 " + result.history().get(59).score());
    }

    @Test
    void kMeansShouldConvergeOnThreeBlobs() {
        MlSimulationEngine.Dataset dataset = threeBlobs();
        MlAdvancedEngine.ClusterResult result = MlAdvancedEngine.trainKMeans(dataset, 3, 20);

        assertEquals(3, result.centroids().size());
        assertEquals(dataset.points().size(), result.assignments().size());
        // 惯性应随迭代不增
        for (int i = 1; i < result.history().size(); i++) {
            assertTrue(result.history().get(i).loss() <= result.history().get(i - 1).loss() + 1e-6,
                    "第 " + i + " 步惯性不应上升");
        }
        assertTrue(result.history().get(19).score() > 0.4,
                "轮廓系数应大于 0.4，实际 " + result.history().get(19).score());
    }

    @Test
    void pcaShouldExplainMostVarianceWithTwoComponents() throws Exception {
        MlSimulationEngine.Dataset dataset = irisFull();
        MlAdvancedEngine.PcaResult result = MlAdvancedEngine.computePca(dataset, 2);

        assertEquals(2, result.components().size());
        assertEquals(dataset.points().size(), result.projections().size());
        assertEquals(2, result.projections().get(0).size());
        double cumulative = result.cumulativeVariance().get(1);
        assertTrue(cumulative > 0.9, "前两个主成分应解释超过 90% 方差，实际 " + cumulative);
        assertTrue(result.explainedVariance().get(0) >= result.explainedVariance().get(1));
    }

    @Test
    void randomForestShouldClassifyIris() throws Exception {
        MlSimulationEngine.Dataset dataset = iris2d();
        MlAdvancedEngine.ForestResult single = MlAdvancedEngine.trainRandomForest(dataset, 8, 3, 1);
        MlAdvancedEngine.ForestResult forest = MlAdvancedEngine.trainRandomForest(dataset, 8, 3, 8);

        assertEquals(1, single.history().size());
        assertEquals(8, forest.history().size());
        assertTrue(forest.history().get(7).score() > single.history().get(0).score(),
                "树越多准确率不应下降");
        assertTrue(forest.history().get(7).score() > 0.85,
                "森林准确率应大于 0.85，实际 " + forest.history().get(7).score());
    }

    // ==================== 测试数据 ====================

    private MlSimulationEngine.Dataset twoClassBlobs() {
        Random random = new Random(7L);
        List<MlSimulationEngine.Point> points = new ArrayList<>();
        double[][] centers = {{0.35, 0.3}, {0.68, 0.72}};
        for (int label = 0; label < 2; label++) {
            for (int i = 0; i < 60; i++) {
                points.add(new MlSimulationEngine.Point(
                        new double[]{
                                centers[label][0] + random.nextGaussian() * 0.09,
                                centers[label][1] + random.nextGaussian() * 0.09},
                        null, label));
            }
        }
        return new MlSimulationEngine.Dataset(List.of("特征 x", "特征 y"), "类别",
                List.of("A 类", "B 类"), points);
    }

    private MlSimulationEngine.Dataset threeBlobs() {
        Random random = new Random(11L);
        List<MlSimulationEngine.Point> points = new ArrayList<>();
        double[][] centers = {{0.25, 0.3}, {0.7, 0.35}, {0.5, 0.75}};
        for (double[] center : centers) {
            for (int i = 0; i < 40; i++) {
                points.add(new MlSimulationEngine.Point(
                        new double[]{
                                center[0] + random.nextGaussian() * 0.08,
                                center[1] + random.nextGaussian() * 0.08},
                        null, null));
            }
        }
        return new MlSimulationEngine.Dataset(List.of("特征 x", "特征 y"), null, null, points);
    }

    private MlSimulationEngine.Dataset iris2d() throws Exception {
        List<MlSimulationEngine.Point> points = new ArrayList<>();
        for (double[] row : readCsv("ml/iris.csv", 2)) {
            points.add(new MlSimulationEngine.Point(new double[]{row[0], row[1]}, null, (int) row[2]));
        }
        return new MlSimulationEngine.Dataset(List.of("花瓣长度", "花瓣宽度"), "品种",
                List.of("setosa", "versicolor", "virginica"), points);
    }

    private MlSimulationEngine.Dataset irisFull() throws Exception {
        List<MlSimulationEngine.Point> points = new ArrayList<>();
        for (double[] row : readCsv("ml/iris_full.csv", 4)) {
            points.add(new MlSimulationEngine.Point(new double[]{row[0], row[1], row[2], row[3]}, null, null));
        }
        return new MlSimulationEngine.Dataset(List.of("花萼长度", "花萼宽度", "花瓣长度", "花瓣宽度"),
                null, null, points);
    }

    private List<double[]> readCsv(String resource, int numericColumns) throws Exception {
        List<double[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(resource), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(",");
                double[] row = new double[numericColumns + 1];
                for (int i = 0; i <= numericColumns; i++) {
                    row[i] = Double.parseDouble(cells[i].trim());
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
