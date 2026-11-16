-- 创建数据库
CREATE DATABASE IF NOT EXISTS ct_platform
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE ct_platform;

-- 用户表
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
  `password` VARCHAR(255) NOT NULL COMMENT 'bcrypt加密后的密码',
  `role` VARCHAR(20) NOT NULL DEFAULT 'student' COMMENT '角色: student/teacher/admin',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `status` INT NOT NULL DEFAULT 1 COMMENT '账号状态: 1正常 0禁用',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户会话表（用于JWT黑名单管理）
CREATE TABLE IF NOT EXISTS `user_sessions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `token_jti` VARCHAR(64) NOT NULL UNIQUE COMMENT 'JWT ID（用于撤销）',
  `device_info` VARCHAR(255) DEFAULT NULL COMMENT '设备信息',
  `ip_address` VARCHAR(45) DEFAULT NULL COMMENT 'IP地址',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` DATETIME NOT NULL COMMENT 'Token过期时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_token_jti` (`token_jti`),
  INDEX `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话表';

-- 插入测试用户（密码为: Pass1234，使用BCrypt加密）
-- BCrypt加密后的密码: $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.qV5zCnLIJ7H6LK
INSERT IGNORE INTO `users` (`username`, `email`, `password`, `role`, `status`) VALUES
('admin', 'admin@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.qV5zCnLIJ7H6LK', 'admin', 1),
('teacher01', 'teacher01@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.qV5zCnLIJ7H6LK', 'teacher', 1),
('student01', 'student01@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.qV5zCnLIJ7H6LK', 'student', 1);

-- ============================================================
-- 第 08 组（机器学习模块）新增表
-- 说明：仅新增表，未修改已有 users / user_sessions 结构，旧库可直接执行本段完成升级
-- ============================================================

-- 机器学习数据集表
CREATE TABLE IF NOT EXISTS `ml_dataset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` VARCHAR(50) NOT NULL COMMENT '数据集编码',
  `name` VARCHAR(100) NOT NULL COMMENT '数据集名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '数据集说明',
  `algorithm` VARCHAR(128) NOT NULL COMMENT '适用算法，逗号分隔（如 decision_tree,random_forest）',
  `feature_names` VARCHAR(255) NOT NULL COMMENT '特征名 JSON 数组',
  `target_name` VARCHAR(50) DEFAULT NULL COMMENT '目标列名',
  `class_names` VARCHAR(255) DEFAULT NULL COMMENT '类别名 JSON 数组，回归任务为 NULL',
  `point_count` INT NOT NULL DEFAULT 0 COMMENT '样本数',
  `data_json` LONGTEXT NOT NULL COMMENT '样本点 JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器学习数据集';

-- 机器学习训练会话表
CREATE TABLE IF NOT EXISTS `ml_training_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `algorithm` VARCHAR(50) NOT NULL COMMENT '算法: linear_regression / decision_tree',
  `dataset_id` BIGINT NOT NULL COMMENT '数据集ID',
  `parameters` VARCHAR(500) DEFAULT NULL COMMENT '训练参数 JSON: learningRate / maxDepth',
  `current_step` INT NOT NULL DEFAULT 0 COMMENT '当前步数',
  `total_steps` INT NOT NULL DEFAULT 0 COMMENT '总步数',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ready' COMMENT '状态: ready / training / finished',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dataset_id` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器学习训练会话';

-- 旧库升级（幂等）：algorithm 从 VARCHAR(50) 放宽到 128，以容纳多算法数据集
ALTER TABLE `ml_dataset` MODIFY COLUMN `algorithm` VARCHAR(128) NOT NULL COMMENT '适用算法，逗号分隔';
