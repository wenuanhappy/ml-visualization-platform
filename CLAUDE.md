# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

机器学习指导仪表板 —— 交互式机器学习教学平台，覆盖七个传统算法（线性回归、逻辑回归、
决策树、SVM、K-Means、PCA、随机森林）的分步训练、可视化与指标追踪。

技术栈：Vue 3 + Vite + ECharts（前端）、Spring Boot 3.2 + MyBatis-Plus + MySQL 8（后端）。
目录结构与包名刻意与课程主平台 ct-platform-v2 保持一致，便于用脚本同步过去。

## 开发命令

```bash
./scripts/start-dev.sh          # 自带 MySQL（.dev/mysql, 3308）+ 后端 8080 + 前端 5174
./scripts/start-dev.sh --with-platform   # 再加主平台前端 5173
./scripts/stop-dev.sh           # 关闭（--mysql 连数据库一起关）
./scripts/db.sh status|ensure|client     # 项目数据库管理

cd frontend && npm run dev      # 只跑前端
cd backend && mvn spring-boot:run        # 只跑后端（需 JDK 17 与 DB_* 环境变量）
./scripts/sync-to-ct-platform.sh         # 同步到 ct-platform-v2 并跑构建
```

## 关键约束

- **JDK 必须 17**：`/usr/libexec/java_home -v 17` 可能返回更高版本，Lombok 在 javac 23+
  上会静默失效（表现为大量「找不到符号」）。脚本里用 `java -version` 校验主版本号。
- **后端运行时不要 `mvn package`**：会覆盖运行中的 jar，触发 `NoClassDefFoundError`。
  先 `stop-dev.sh`，打包后再启动。
- **不要硬编码数据库密码**：`.dev/env`（gitignore）提供 `DB_USERNAME` / `DB_PASSWORD` / `DB_PORT`。

## 架构要点

### 前端（frontend/src）

- `views/lab/ExperimentView.vue`：训练中心主页面，三栏布局（算法/数据集/对比/特征选择 |
  画布+评估 | 参数/训练控制/日志/解释），hero 状态条，底部案例库，AI 助手浮窗
- `views/lab/LearningView.vue`：学习中心（8 节课、200 道题库、错题集），路由用 keep-alive 保活
- `components/lab/VisualizationCanvas.vue`：ECharts 画布。轴选项 = 数据集特征（+ 回归数据集的目标列，
  PCA 时前置 PC1..PCn）；决策边界全部来自后端投影接口；含 PCA 3D 拖拽视图与特征重要性小图
- `services/*.js`：算法参数定义、评估指标推导、解释文案、案例预设、课程与题库
- API 统一走 `@/utils/request`（返回 `{ code, message, data, timestamp }` 的 body）

### 后端（backend/src/main/java/com/ctplatform）

- `controller/MlModuleController.java`：`/api/ml/**`，含数据集、会话、投影、对比、特征选择、AI 助手
- `service/MlSimulationEngine.java`：线性回归（z-score + 动量梯度下降）、决策树（基尼贪心 + BFS 展开）
- `service/MlAdvancedEngine.java`：逻辑回归、SVM（linear/rbf/poly）、K-Means、PCA、随机森林
- `service/impl/MlTrainingServiceImpl.java`：会话按步数确定性重算；PCA 一次性算完；
  投影把模型切到所选两个维度；特征重要性只给随机森林
- `service/MlDatasetSeeder.java`：启动时幂等写入 iris / boston_housing / make_classification / make_blobs
- 接口返回统一 `ApiResponse`，实体用 Lombok + MyBatis-Plus

### 数据与算法约定

- 数据集不限制算法：缺 target 时用 label 兜底，缺 label 时按 target 中位数二分
- 归一化：线性回归 / 逻辑回归 / SVM / K-Means / PCA 都用 z-score；树模型不归一化
- 投影/对比/特征选择都在后端算，前端只负责画
