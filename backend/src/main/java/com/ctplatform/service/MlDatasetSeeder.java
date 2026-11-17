package com.ctplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ctplatform.entity.MlDataset;
import com.ctplatform.mapper.MlDatasetMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 机器学习数据集初始化（第 08 组新增）。
 *
 * <p>启动时保证五个演示数据集存在，幂等：按 code 判断，已存在则只在算法列表变化时更新。
 * 初始化失败只记日志，不影响平台启动——与主平台「初始化不能阻断启动」的既有约定一致。</p>
 */
@Component
public class MlDatasetSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MlDatasetSeeder.class);

    @Autowired
    private MlDatasetMapper datasetMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedIris();
            seedBoston();
            seedMakeClassification();
            seedMakeBlobs();
            removeLegacy("classification_2d");
            removeLegacy("blobs_3c");
            removeLegacy("iris_2d");
            removeLegacy("iris_full");
            removeLegacy("regression_synthetic");
        } catch (Exception e) {
            log.warn("机器学习数据集初始化失败（可能是尚未执行 schema.sql）：{}", e.getMessage());
        }
    }

    /** UCI 鸢尾花完整四维特征（原项目同名数据集） */
    private void seedIris() throws Exception {
        List<double[]> rows = readCsv("ml/iris_full.csv", 4);
        List<Map<String, Object>> points = new ArrayList<>();
        for (double[] row : rows) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("features", List.of(row[0], row[1], row[2], row[3]));
            point.put("label", (int) row[4]);
            points.add(point);
        }
        upsert("iris", "鸢尾花（Iris）",
                "UCI Iris 完整四个特征（花萼长宽、花瓣长宽），三分类，共 150 条。可配合任意算法使用，四维数据也可直接喂给决策树。",
                "decision_tree,random_forest,logistic_regression,support_vector_machine,pca,k_means",
                List.of("花萼长度", "花萼宽度", "花瓣长度", "花瓣宽度"), "品种",
                List.of("setosa", "versicolor", "virginica"), points);
    }

    /** 波士顿房价：13 个数值特征 + 房价中位数，与原项目同源同名 */
    private void seedBoston() throws Exception {
        List<double[]> rows = readCsv("ml/boston.csv", 13);
        List<Map<String, Object>> points = new ArrayList<>();
        for (double[] row : rows) {
            List<Double> features = new ArrayList<>(13);
            for (int i = 0; i < 13; i++) {
                features.add(row[i]);
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("features", features);
            point.put("target", row[13]);
            points.add(point);
        }
        upsert("boston_housing", "波士顿房价",
                "经典回归数据集：13 个数值特征（犯罪率、住宅用地比例、房间数、低收入比例、税率等）预测房价中位数，共 506 条。",
                "linear_regression,decision_tree,random_forest,pca,k_means",
                List.of("城镇犯罪率", "住宅用地比例", "非零售商业用地比例", "是否临河", "一氧化氮浓度",
                        "住宅平均房间数", "1940年前建成比例", "到就业中心距离", "公路可达性指数",
                        "房产税率", "师生比", "黑人比例指数", "低收入人口比例"),
                "房屋中位价（千美元）", null, points);
    }

    /** 两个高斯簇：类别 0 中心 (2.0,2.0)，类别 1 中心 (4.5,4.5)，σ=0.9，种子 42（与原项目一致） */
    private void seedMakeClassification() throws Exception {
        Random random = new Random(42L);
        List<Map<String, Object>> points = new ArrayList<>();
        double[][] centers = {{2.0, 2.0}, {4.5, 4.5}};
        for (int label = 0; label < 2; label++) {
            for (int i = 0; i < 100; i++) {
                double x = centers[label][0] + random.nextGaussian() * 0.9;
                double y = centers[label][1] + random.nextGaussian() * 0.9;
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("features", List.of(round(x), round(y)));
                point.put("label", label);
                points.add(point);
            }
        }
        upsert("make_classification", "make_classification（合成二分类）",
                "两个高斯簇，中心 (2,2) 与 (4.5,4.5)，σ=0.9，种子 42，两簇轻微重叠，用于观察分类边界如何被推过去。",
                "logistic_regression,support_vector_machine,decision_tree,random_forest",
                List.of("特征 x", "特征 y"), "类别",
                List.of("类别 0", "类别 1"), points);
    }

    /** 三个高斯簇：中心 (1.5,2.0)、(5.3,6.3)、(8.3,9.0)，σ=0.6，种子 42（与原项目一致） */
    private void seedMakeBlobs() throws Exception {
        Random random = new Random(42L);
        List<Map<String, Object>> points = new ArrayList<>();
        double[][] centers = {{1.5, 2.0}, {5.3, 6.3}, {8.3, 9.0}};
        for (int cluster = 0; cluster < centers.length; cluster++) {
            double[] center = centers[cluster];
            for (int i = 0; i < 50; i++) {
                double x = center[0] + random.nextGaussian() * 0.6;
                double y = center[1] + random.nextGaussian() * 0.6;
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("features", List.of(round(x), round(y)));
                point.put("label", cluster);
                points.add(point);
            }
        }
        upsert("make_blobs", "make_blobs（合成三簇）",
                "三个高斯簇，中心 (1.5,2.0)、(5.3,6.3)、(8.3,9.0)，σ=0.6，种子 42，无标签，用于 K-Means 聚类演示。",
                "k_means",
                List.of("特征 x", "特征 y"), null,
                List.of("簇 1", "簇 2", "簇 3"), points);
    }

    /** 删除早期版本遗留的数据集编码 */
    private void removeLegacy(String code) {
        int deleted = datasetMapper.delete(new QueryWrapper<MlDataset>().eq("code", code));
        if (deleted > 0) {
            log.info("已清理早期数据集：{}", code);
        }
    }

    private List<double[]> readCsv(String resourcePath, int numericColumns) throws Exception {
        List<double[]> rows = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // 跳过表头
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

    private void upsert(String code, String name, String description, String algorithms,
                        List<String> featureNames, String targetName, List<String> classNames,
                        List<Map<String, Object>> points) throws Exception {
        MlDataset existing = datasetMapper.selectOne(new QueryWrapper<MlDataset>().eq("code", code));
        String featureJson = objectMapper.writeValueAsString(featureNames);
        String dataJson = objectMapper.writeValueAsString(points);
        if (existing != null) {
            // 内容变了（特征维度或样本数）就重建，保证与代码里的定义一致
            boolean contentChanged = existing.getPointCount() == null
                    || existing.getPointCount() != points.size()
                    || !featureJson.equals(existing.getFeatureNames())
                    || !dataJson.equals(existing.getDataJson());
            if (contentChanged) {
                existing.setName(name);
                existing.setDescription(description);
                existing.setAlgorithm(algorithms);
                existing.setFeatureNames(featureJson);
                existing.setTargetName(targetName);
                existing.setClassNames(classNames == null ? null : objectMapper.writeValueAsString(classNames));
                existing.setPointCount(points.size());
                existing.setDataJson(dataJson);
                datasetMapper.updateById(existing);
                log.info("已重建数据集 {}（{} 个样本 / {} 个特征）", code, points.size(), featureNames.size());
                return;
            }
            if (!algorithms.equals(existing.getAlgorithm())) {
                existing.setAlgorithm(algorithms);
                datasetMapper.updateById(existing);
                log.info("已更新数据集 {} 的适用算法为 {}", code, algorithms);
            }
            return;
        }
        MlDataset dataset = new MlDataset();
        dataset.setCode(code);
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setAlgorithm(algorithms);
        dataset.setFeatureNames(objectMapper.writeValueAsString(featureNames));
        dataset.setTargetName(targetName);
        if (classNames != null) {
            dataset.setClassNames(objectMapper.writeValueAsString(classNames));
        }
        dataset.setPointCount(points.size());
        dataset.setDataJson(objectMapper.writeValueAsString(points));
        datasetMapper.insert(dataset);
        log.info("已初始化机器学习数据集：{}（{} 个样本）", code, points.size());
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
