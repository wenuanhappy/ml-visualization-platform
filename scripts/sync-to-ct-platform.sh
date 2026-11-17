#!/usr/bin/env bash
#
# 把本项目（机器学习指导仪表板）已适配好的代码同步到 ct-platform-v2。
#
# 用法：
#   ./scripts/sync-to-ct-platform.sh                 # 默认同步到 ~/projects/ct-platform-v2
#   CT_PLATFORM_DIR=/path/to/ct-platform-v2 ./scripts/sync-to-ct-platform.sh
#   ./scripts/sync-to-ct-platform.sh --check         # 只检查文件是否齐全
#
# 两边包名同为 com.ctplatform、前端都用 @/ 别名、api 层都走 @/utils/request，
# 因此文件可以逐字复制，不需要任何文本替换。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DST="${CT_PLATFORM_DIR:-$HOME/projects/ct-platform-v2}"
CHECK_ONLY=0

for arg in "$@"; do
  case "$arg" in
    --check) CHECK_ONLY=1 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

if [ ! -d "$DST/backend/src/main/java/com/ctplatform" ]; then
  echo "找不到 ct-platform-v2 仓库：$DST" >&2
  echo "用 CT_PLATFORM_DIR 指定路径，或先 git clone。" >&2
  exit 1
fi

ML_BACKEND="backend/src/main/java/com/ctplatform"

FILES=(
  # 前端：接口、服务层、组件、页面
  "frontend/src/api/ml.js"
  "frontend/src/services/algorithms.js"
  "frontend/src/services/pcaLabels.js"
  "frontend/src/services/cases.js"
  "frontend/src/services/evaluation.js"
  "frontend/src/services/explanations.js"
  "frontend/src/services/lessons.js"
  "frontend/src/services/questionBank.js"
  "frontend/src/components/lab/AiAssistant.vue"
  "frontend/src/components/lab/AlgorithmSelector.vue"
  "frontend/src/components/lab/CaseLibrary.vue"
  "frontend/src/components/lab/DatasetSelector.vue"
  "frontend/src/components/lab/ComparisonPanel.vue"
  "frontend/src/components/lab/EvaluationPanel.vue"
  "frontend/src/components/lab/FeatureSelectionPanel.vue"
  "frontend/src/components/lab/ModelExplanation.vue"
  "frontend/src/components/lab/ParamPanel.vue"
  "frontend/src/components/lab/TrainingLog.vue"
  "frontend/src/components/lab/VisualizationCanvas.vue"
  "frontend/src/views/lab/ExperimentView.vue"
  "frontend/src/views/lab/LearningView.vue"
  # 后端：接口、实体、Mapper、DTO、引擎、初始化器
  "${ML_BACKEND}/controller/MlModuleController.java"
  "${ML_BACKEND}/dto/MlDatasetDto.java"
  "${ML_BACKEND}/dto/MlProjectionDto.java"
  "${ML_BACKEND}/dto/MlTrainRequest.java"
  "${ML_BACKEND}/dto/MlTrainingResponse.java"
  "${ML_BACKEND}/entity/MlDataset.java"
  "${ML_BACKEND}/entity/MlTrainingSession.java"
  "${ML_BACKEND}/mapper/MlDatasetMapper.java"
  "${ML_BACKEND}/mapper/MlTrainingSessionMapper.java"
  "${ML_BACKEND}/service/MlAdvancedEngine.java"
  "${ML_BACKEND}/service/MlDatasetSeeder.java"
  "${ML_BACKEND}/service/MlSimulationEngine.java"
  "${ML_BACKEND}/service/MlTrainingService.java"
  "${ML_BACKEND}/service/impl/MlTrainingServiceImpl.java"
  # 数据集资源
  "backend/src/main/resources/ml/iris.csv"
  "backend/src/main/resources/ml/iris_full.csv"
  "backend/src/main/resources/ml/boston.csv"
  # 测试
  "backend/src/test/java/com/ctplatform/service/MlSimulationEngineTest.java"
  "backend/src/test/java/com/ctplatform/service/MlAdvancedEngineTest.java"
)

echo "源仓库：$REPO_ROOT"
echo "目标仓库：$DST"
echo

missing=0
for file in "${FILES[@]}"; do
  if [ ! -f "$REPO_ROOT/$file" ]; then
    echo "  ✗ 源文件不存在：$file" >&2
    missing=1
  fi
done
if [ "$missing" -ne 0 ]; then
  exit 1
fi

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo "✓ 全部 ${#FILES[@]} 个文件就位（--check 模式，未写入）"
  exit 0
fi

for file in "${FILES[@]}"; do
  mkdir -p "$DST/$(dirname "$file")"
  cp "$REPO_ROOT/$file" "$DST/$file"
  echo "  → $file"
done

# schema.sql 是公共文件：只在缺少 ML 段时追加，绝不整文件覆盖
if ! grep -q "ml_training_session" "$DST/backend/src/main/resources/schema.sql"; then
  echo
  echo "schema.sql 里没有 ML 段，正在追加……"
  bash "$REPO_ROOT/scripts/append-ml-schema.sh" >> "$DST/backend/src/main/resources/schema.sql"
  echo "  → 已追加 ml_dataset / ml_training_session 与旧库升级语句"
fi

# ECharts 是画布与对比图的依赖；平台原本没有，缺了就装一次
if ! grep -q '"echarts"' "$DST/frontend/package.json"; then
  echo
  echo "目标仓库缺少 echarts，安装中（会修改 package.json / package-lock.json）……"
  ( cd "$DST/frontend" && npm install --no-audit --no-fund echarts@^5.5.0 > /dev/null )
  echo "  → 已安装 echarts（公共文件变更，需要单独 review）"
fi

echo
echo "开始构建校验……"
# 平台要求 JDK 17；很多机器默认 JAVA_HOME 更新，Lombok 在新 javac 上会失效，所以显式指定
JDK17="${CT_JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
if [ ! -x "$JDK17/bin/javac" ]; then
  echo "找不到 JDK 17：$JDK17（可用 CT_JAVA_HOME 指定）" >&2
  exit 1
fi
(
  cd "$DST/backend"
  JAVA_HOME="$JDK17" PATH="$JDK17/bin:$PATH" mvn -B -q -DskipTests package
)
echo "✓ 后端构建通过"
(
  cd "$DST/frontend"
  npm run build > /dev/null
)
echo "✓ 前端构建通过"

echo
echo "同步完成。接下来："
echo "  cd $DST && git status"
