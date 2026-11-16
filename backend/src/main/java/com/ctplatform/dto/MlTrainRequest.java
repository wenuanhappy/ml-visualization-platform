package com.ctplatform.dto;

import lombok.Data;

import java.util.Map;

/**
 * 创建训练会话的请求体（第 08 组新增）。
 */
@Data
public class MlTrainRequest {

    /** linear_regression / decision_tree */
    private String algorithm;

    private Long datasetId;

    /** 训练参数，如 {"learningRate":0.03} 或 {"maxDepth":3}；不传则使用默认值 */
    private Map<String, Object> parameters;
}
