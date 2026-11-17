# Machine Learning Visualization Platform

机器学习指导仪表板 —— 交互式机器学习教学项目，覆盖七个经典算法的分步训练、可视化与指标追踪。

**本仓库是 ct-platform-v2 的「开发主线」**：代码按平台的技术栈与目录约定开发，
通过 `scripts/sync-to-ct-platform.sh` 同步到课程主平台。

> 迁移说明：项目原先用 Angular 17 实现，现已完整迁移到 Vue 3 并移除了旧的 `源代码/` 目录。
> 如需查看迁移前的实现，可从 git 历史中取：`git show <commit>:源代码/...`。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.4 + Vite 5 + ECharts 5 + Vue Router |
| 后端 | Spring Boot 3.2.5 + MyBatis-Plus 3.5.6 + MySQL 8 + JWT |
| 构建 | npm + Maven 3.8+ / JDK 17 |

与 ct-platform-v2 保持一致：包名 `com.ctplatform`、前端 `@/` 别名、
接口统一返回 `{ code, message, data, timestamp }`、后端只依赖 Maven + 本地 MySQL，
**不需要 Docker**。

## 目录结构

```
ml-visualization-platform/
├── frontend/                      Vue 3 前端
│   └── src/
│       ├── api/ml.js              接口封装（走 @/utils/request）
│       ├── components/lab/        画布、评估面板、参数面板、日志、解释、
│       │                          案例库、模型对比、特征选择、AI 助手
│       ├── services/              算法参数、评估推导、解释文案、案例、课程与题库
│       └── views/lab/             训练中心 / 学习中心
├── backend/                       Spring Boot 后端（com.ctplatform）
│   └── src/main/
│       ├── java/com/ctplatform/
│       │   ├── controller/MlModuleController.java    /api/ml/**
│       │   ├── service/MlSimulationEngine.java       线性回归 + 决策树
│       │   ├── service/MlAdvancedEngine.java         逻辑回归 / SVM(linear·rbf·poly) /
│       │   │                                         K-Means / PCA / 随机森林
│       │   └── service/impl/MlTrainingServiceImpl.java  会话、投影、对比、特征选择、AI
│       └── resources/
│           ├── ml/*.csv           内置数据集（UCI Iris、Boston Housing）
│           └── schema.sql         含 ml_dataset / ml_training_session
├── docs/parity.md                 迁移对照表与已知差异
└── scripts/
    ├── start-dev.sh               一键启动（自带 MySQL 实例，无需 sudo）
    ├── stop-dev.sh                一键关闭
    ├── db.sh                      项目自带 MySQL（.dev/mysql, 3308）
    └── sync-to-ct-platform.sh     同步到课程主平台
```

## 支持的算法

| 算法 | 任务 | 可视化 |
|---|---|---|
| 线性回归 | 回归 | 拟合直线 + MSE / R² |
| 逻辑回归 | 二分类 | 决策边界 + 交叉熵 / 准确率 |
| 支持向量机 | 二分类 | 线性核直线，rbf / poly 核为采样曲线 |
| K-Means | 聚类 | 质心移动 + 惯性 / 轮廓系数 |
| PCA | 降维 | 主成分投影、方差解释率、载荷与含义 |
| 随机森林 | 分类 | 逐棵树投票 + 特征重要性 |
| 决策树 | 分类 | 分裂阈值线 + 节点结构 |

## 一键启停（推荐）

```bash
./scripts/start-dev.sh                  # 数据库 + 后端 8080 + 前端 5174
./scripts/start-dev.sh --with-platform  # 再加主平台前端 5173
./scripts/stop-dev.sh                   # 关闭
./scripts/stop-dev.sh --mysql           # 连项目数据库一起关
```

- 数据库是**项目自带实例**（`.dev/mysql`，端口 3308），以当前用户身份运行，**不需要 sudo、不需要 root 密码**
- 首次运行会自动初始化数据目录、建库建号、导入 schema，并从系统 3306 实例迁移一次数据
- 日志与 PID 在 `.dev/`（已 gitignore）；数据库账号密码在 `.dev/env`

## 手动运行

```bash
# 1. 数据库
./scripts/db.sh ensure

# 2. 后端（必须 JDK 17）
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
DB_USERNAME=ct_app DB_PASSWORD='取自 .dev/env' \
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3308/ct_platform?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' \
mvn spring-boot:run

# 3. 前端
cd frontend
npm install
npm run dev          # http://localhost:5174
```

数据集在后端首次启动时自动写入，幂等，不需要手工导入。

> 测试账号：`users` 表里的种子账号密码哈希是占位值，登不进去；
> 请用页面注册自己的账号。

## AI 助教（可选）

后端从环境变量读取，仓库里不保存任何 key：

```bash
export LLM_API_KEY=sk-...
export LLM_ENDPOINT=https://api.openai.com/v1   # 可选
export LLM_MODEL=gpt-3.5-turbo                  # 可选
```

未配置时，助教会直接回复「尚未配置」的提示，不影响其余功能。

## 同步到 ct-platform-v2

```bash
./scripts/sync-to-ct-platform.sh --check   # 只检查文件是否齐全
./scripts/sync-to-ct-platform.sh           # 复制 + 跑后端/前端构建
cd ~/projects/ct-platform-v2 && git status
```

脚本只复制本组文件，不覆盖平台的 `application.yml`、`ModuleView.vue` 等公共文件；
`schema.sql` 只在缺少 ML 段时追加。平台侧的模块注册只有 `ModuleView.vue` 的 `modules.ai` 一行。

## 注意事项

- **后端在跑的时候不要 `mvn package`**：会覆盖正在运行的 jar，Spring Boot 懒加载会抛
  `NoClassDefFoundError`。改用脚本启停，或先停后打包再启动。
- JDK 必须是 17：Lombok 在 javac 23+ 上会失效，表现为大量「找不到符号」。
- 项目不再包含 Docker 相关文件；本地依赖只有 MySQL 8 + JDK 17 + Maven + Node 18+。
