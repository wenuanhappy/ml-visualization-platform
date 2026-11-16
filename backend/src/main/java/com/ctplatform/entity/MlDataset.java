package com.ctplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 机器学习数据集。
 *
 * <p>第 08 组（机器学习模块）新增，对应 {@code ml_dataset} 表。
 * {@code dataJson} 中保存样本点数组，结构为
 * {@code [{"features":[1.4,0.2],"label":0}, ...]}；回归数据集用 {@code target} 代替 {@code label}。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("ml_dataset")
public class MlDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据集编码，如 iris_2d / regression_synthetic */
    private String code;

    private String name;

    private String description;

    /** 适用算法：linear_regression / decision_tree */
    private String algorithm;

    /** 特征名 JSON 数组，如 ["petal_length","petal_width"] */
    private String featureNames;

    /** 目标列名，如 类别 / 成绩 */
    private String targetName;

    /** 类别名 JSON 数组，回归任务为 null */
    private String classNames;

    private Integer pointCount;

    /** 样本点 JSON */
    private String dataJson;

    private LocalDateTime createdAt;
}
