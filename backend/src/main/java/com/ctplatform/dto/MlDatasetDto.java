package com.ctplatform.dto;

import lombok.Data;

import java.util.List;

/**
 * 机器学习数据集 DTO（第 08 组新增）。
 */
@Data
public class MlDatasetDto {

    private Long id;

    private String code;

    private String name;

    private String description;

    /** linear_regression / decision_tree */
    private String algorithm;

    private List<String> featureNames;

    private String targetName;

    /** 回归任务为 null */
    private List<String> classNames;

    private Integer pointCount;

    private List<PointDto> points;

    @Data
    public static class PointDto {
        private List<Double> features;
        /** 回归任务的目标值，分类任务为 null */
        private Double target;
        /** 分类任务的类别下标，回归任务为 null */
        private Integer label;
    }

    /** 数据集摘要（不含样本点，用于列表） */
    @Data
    public static class Summary {
        private Long id;
        private String code;
        private String name;
        private String description;
        private String algorithm;
        private List<String> featureNames;
        private String targetName;
        private List<String> classNames;
        private Integer pointCount;
    }
}
