package com.ctplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 机器学习训练会话。
 *
 * <p>第 08 组（机器学习模块）新增，对应 {@code ml_training_session} 表。</p>
 *
 * <p>设计取舍：会话只持久化「算法 + 数据集 + 参数 + 当前步数」，不持久化模型快照。
 * 训练过程是确定性的，每次请求按当前步数从数据集重新推到该步，
 * 这样避免了增量写模型 JSON 带来的漂移和并发脏数据，也让「重置」退化成把 currentStep 置 0。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("ml_training_session")
public class MlTrainingSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 算法：linear_regression / decision_tree */
    private String algorithm;

    private Long datasetId;

    /** 训练参数 JSON，如 {"learningRate":0.03} 或 {"maxDepth":3} */
    private String parameters;

    private Integer currentStep;

    private Integer totalSteps;

    /** ready / training / finished */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
