package com.ctplatform.service;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 机器学习仿真核心的单元测试（第 08 组新增）。
 *
 * <p>不依赖数据库与 Spring 容器，CI 里的 {@code mvn test} 即可覆盖算法正确性。</p>
 */
class MlSimulationEngineTest {

    @Test
    void linearRegressionShouldRecoverSlopeAndIntercept() {
        Random random = new Random(42L);
        List<MlSimulationEngine.Point> points = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            double x = 0.5 + random.nextDouble() * 9.0;
            double y = 2.5 * x + 1.0 + random.nextGaussian() * 0.8;
            points.add(new MlSimulationEngine.Point(new double[]{x}, y, null));
        }
        MlSimulationEngine.Dataset dataset = new MlSimulationEngine.Dataset(
                List.of("学习时长(h)"), "成绩", null, points);

        MlSimulationEngine.LinearRegressionResult result =
                MlSimulationEngine.trainLinearRegression(dataset, 0.1, 60);

        assertEquals(60, result.history().size());
        double slope = result.weights()[0];
        double intercept = result.bias();
        assertTrue(Math.abs(slope - 2.5) < 0.15, "斜率应接近 2.5，实际 " + slope);
        assertTrue(Math.abs(intercept - 1.0) < 0.6, "截距应接近 1.0，实际 " + intercept);
        assertTrue(result.history().get(59).score() > 0.9,
                "R² 应大于 0.9，实际 " + result.history().get(59).score());
        // 损失应随步数单调不增
        for (int i = 1; i < result.history().size(); i++) {
            assertTrue(result.history().get(i).loss() <= result.history().get(i - 1).loss() + 1e-6);
        }
        assertNotNull(result.regressionLine());
        assertEquals(2, result.regressionLine().size());
    }

    @Test
    void decisionTreeShouldFitIrisWell() throws Exception {
        MlSimulationEngine.Dataset dataset = loadIris();
        MlSimulationEngine.TreeResult tree = MlSimulationEngine.buildDecisionTree(dataset, 3, 2);

        assertTrue(tree.splitCount() > 0, "树至少应有一次分裂");
        assertFalse(tree.nodes().isEmpty());
        MlSimulationEngine.MetricPoint finalMetric = tree.history().get(tree.splitCount());
        assertTrue(finalMetric.score() > 0.9, "训练集准确率应大于 0.9，实际 " + finalMetric.score());
        assertEquals(0, tree.history().get(0).step());

        List<Integer> assignments = MlSimulationEngine.assignClasses(tree.nodes(), dataset.points(), tree.splitCount());
        assertEquals(dataset.points().size(), assignments.size());

        // 一个分裂都没展开时，所有样本都应落在根节点
        List<Integer> none = MlSimulationEngine.assignClasses(tree.nodes(), dataset.points(), 0);
        assertEquals(1, none.stream().distinct().count());
    }

    private MlSimulationEngine.Dataset loadIris() throws Exception {
        List<MlSimulationEngine.Point> points = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("ml/iris.csv"), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(",");
                points.add(new MlSimulationEngine.Point(
                        new double[]{Double.parseDouble(cells[0]), Double.parseDouble(cells[1])},
                        null,
                        Integer.parseInt(cells[2])));
            }
        }
        return new MlSimulationEngine.Dataset(
                List.of("花瓣长度", "花瓣宽度"), "品种",
                List.of("setosa", "versicolor", "virginica"), points);
    }
}
