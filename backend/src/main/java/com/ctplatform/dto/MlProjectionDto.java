package com.ctplatform.dto;

import lombok.Data;

import java.util.List;

/**
 * 模型在「指定两个维度」上的投影结果（第 08 组新增）。
 *
 * <p>对应原项目的 ProjectionDTO：画布只负责把 kind 和 lines 画出来，
 * 具体怎么切面、哪些维度能画，全部由后端决定，避免前后端两套规则漂移。</p>
 */
@Data
public class MlProjectionDto {

    public static final String KIND_REGRESSION_LINE = "regression_line";
    public static final String KIND_DECISION_BOUNDARY = "decision_boundary";
    public static final String KIND_CENTROIDS = "centroids";
    public static final String KIND_NONE = "none";

    private Long sessionId;
    private String xName;
    private String yName;
    private String kind;
    /** 每条线是若干点：[ [x1,y1], [x2,y2] ] */
    private List<List<List<Double>>> lines;
    /** 聚类中心（原始维度坐标） */
    private List<List<Double>> centroids;
    /** 投影说明：为什么画不出来、隐藏维度怎么处理的 */
    private String note;
}
