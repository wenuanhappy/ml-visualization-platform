# Machine Learning Algorithm Visualization Platform

> **迁移说明（2025-11）**：本仓库已迁移到 Vue 3 + Spring Boot（`frontend/` 与 `backend/`），
> 与 ct-platform-v2 同构，可直接同步进主平台。下文描述的 Angular 版本保留在 `源代码/` 目录，
> 仅作参考，不再维护。启动方式见文末「脚本」一节。


机器学习算法可视化教学平台 - 交互式教育平台，展示传统 ML 算法的训练过程。

## 功能特性

- 📊 **算法可视化**: 线性回归、逻辑回归、决策树、SVM、K-Means、PCA、随机森林
- 🎯 **决策边界渲染**: 实时展示算法学习到的决策边界
- 📈 **步骤训练**: 分步执行训练过程，可暂停/继续
- 🤖 **AI 助教**: 内置 AI 助手解答算法相关问题

## 技术栈

**后端**: Spring Boot 3.2 + MyBatis + MySQL 8.0  
**前端**: Angular 17 + ECharts  
**容器化**: Docker + Docker Compose

## 快速开始

### Docker 部署（推荐）

```bash
cd 源代码
docker-compose up -d --build
```

访问 http://localhost

### 本地开发

**后端**
```bash
cd 源代码/backend
mvn spring-boot:run
```

**前端**
```bash
cd 源代码/frontend
npm install
npm start
```

## 项目结构

```
源代码/
├── backend/          # Spring Boot 后端
├── frontend/         # Angular 前端
├── doc/              # 项目文档
├── docker-compose.yml
├── Dockerfile.backend
└── Dockerfile.frontend
```

## 文档

详细文档见 `源代码/doc/` 目录：
- 使用文档.md - 用户使用指南
- 开发文档.md - 开发环境配置
- 部署文档.md - 生产部署指南
- 设计文档.md - 系统设计说明
- 分析文档.md - 项目分析报告

## License

MIT

## AI 助教（可选）

后端从环境变量读取，仓库里不保存任何 key：

```bash
export LLM_API_KEY=sk-...
export LLM_ENDPOINT=https://api.openai.com/v1   # 可选
export LLM_MODEL=gpt-3.5-turbo                  # 可选
```

未配置时，助教会直接回复「尚未配置」的提示，不影响其余功能。

## 脚本

```bash
./scripts/start-dev.sh                  # 数据库 + 后端 8080 + 前端 5174
./scripts/start-dev.sh --with-platform  # 再加主平台 5173
./scripts/stop-dev.sh                   # 关闭
./scripts/db.sh status                  # 项目自带 MySQL 实例（.dev/mysql, 3308）
```
