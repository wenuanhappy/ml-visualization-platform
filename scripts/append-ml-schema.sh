#!/usr/bin/env bash
# 输出 ML 建表语句（供 sync 脚本追加到 ct-platform 的 schema.sql）
set -euo pipefail
cat <<'SQL'

-- ============================================================
-- 第 08 组（机器学习模块）新增表
-- 说明：仅新增表，未修改已有 users / user_sessions 结构，旧库可直接执行本段完成升级
-- ============================================================

CREATE TABLE IF NOT EXISTS `ml_dataset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` VARCHAR(50) NOT NULL COMMENT '数据集编码',
  `name` VARCHAR(100) NOT NULL COMMENT '数据集名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '数据集说明',
  `algorithm` VARCHAR(128) NOT NULL COMMENT '适用算法，逗号分隔',
  `feature_names` VARCHAR(255) NOT NULL COMMENT '特征名 JSON 数组',
  `target_name` VARCHAR(50) DEFAULT NULL COMMENT '目标列名',
  `class_names` VARCHAR(255) DEFAULT NULL COMMENT '类别名 JSON 数组，回归任务为 NULL',
  `point_count` INT NOT NULL DEFAULT 0 COMMENT '样本数',
  `data_json` LONGTEXT NOT NULL COMMENT '样本点 JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器学习数据集';

CREATE TABLE IF NOT EXISTS `ml_training_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `algorithm` VARCHAR(50) NOT NULL COMMENT '算法标识',
  `dataset_id` BIGINT NOT NULL COMMENT '数据集ID',
  `parameters` VARCHAR(500) DEFAULT NULL COMMENT '训练参数 JSON',
  `current_step` INT NOT NULL DEFAULT 0 COMMENT '当前步数',
  `total_steps` INT NOT NULL DEFAULT 0 COMMENT '总步数',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ready' COMMENT '状态: ready / training / finished',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dataset_id` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器学习训练会话';

-- 旧库升级（幂等）：algorithm 放宽到 128，容纳多算法数据集
ALTER TABLE `ml_dataset` MODIFY COLUMN `algorithm` VARCHAR(128) NOT NULL COMMENT '适用算法，逗号分隔';
SQL
