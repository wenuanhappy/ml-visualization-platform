#!/usr/bin/env bash
#
# 启动机器学习可视化平台（后端 + 前端），可选连动主平台前端。
#
#   ./scripts/start-dev.sh                  # 后端 8080 + 自有前端 5174
#   ./scripts/start-dev.sh --with-platform  # 再加主平台前端 5173（需存在 ct-platform-v2）
#   ./scripts/start-dev.sh --mysql          # 顺便尝试启动本机 MySQL
#
# 日志与 PID 都在 .dev/ 下（已 gitignore）。
# 数据库账号密码从 .dev/env 读取；文件不存在时按本机开发默认值生成。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV="$ROOT/.dev"
ENV_FILE="$DEV/env"
PLATFORM_DIR="${CT_PLATFORM_DIR:-$HOME/projects/ct-platform-v2}"

WITH_PLATFORM=0
WITH_MYSQL=0
for arg in "$@"; do
  case "$arg" in
    --with-platform) WITH_PLATFORM=1 ;;
    --mysql) WITH_MYSQL=1 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

mkdir -p "$DEV"

# ---------- 数据库账号 ----------
if [ ! -f "$ENV_FILE" ]; then
  cat > "$ENV_FILE" <<'ENV'
# 本地开发用的数据库账号（已被 .gitignore 忽略，不要提交）
DB_USERNAME=ct_app
DB_PASSWORD=ct_app_dev_2026
ENV
  echo "已生成 ${ENV_FILE}（如需修改账号密码请直接编辑）"
fi
# shellcheck disable=SC1090
source "$ENV_FILE"
: "${DB_USERNAME:?需要 DB_USERNAME}"
: "${DB_PASSWORD:?需要 DB_PASSWORD}"

# ---------- JDK 17 ----------
# 注意：/usr/libexec/java_home -v 17 可能返回更新版本（它按「>=17」匹配），
# 而 Lombok 在 26/27 上会静默失效，所以必须验证主版本号真的是 17。
jdk_is_17() {
  [ -x "$1/bin/java" ] || return 1
  "$1/bin/java" -version 2>&1 | head -1 | grep -q 'version "17' || return 1
}

JDK17=""
for candidate in "${CT_JAVA_HOME:-}" "/opt/homebrew/opt/openjdk@17" "$(/usr/libexec/java_home -v 17 2>/dev/null || true)" "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"; do
  [ -n "$candidate" ] || continue
  if jdk_is_17 "$candidate"; then
    JDK17="$candidate"
    break
  fi
done

if [ -z "$JDK17" ]; then
  echo "找不到 JDK 17（可用 CT_JAVA_HOME 指定）" >&2
  exit 1
fi
export JAVA_HOME="$JDK17"
export PATH="$JDK17/bin:$PATH"
echo "使用 JDK：$JDK17"

# lsof 有时看不到别的用户（如 _mysql）持有的监听端口，先试 TCP 探测再退回 lsof
port_busy() {
  nc -z -G 1 127.0.0.1 "$1" >/dev/null 2>&1 && return 0
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

wait_for_port() {
  local port="$1" label="$2" tries="${3:-60}"
  for _ in $(seq 1 "$tries"); do
    if port_busy "$port"; then
      echo "  ✓ ${label} 已就绪（:${port}）"
      return 0
    fi
    sleep 1
  done
  echo "  ✗ $label 启动超时，看日志：$DEV/$label.log" >&2
  return 1
}

# ---------- MySQL ----------
if port_busy 3306; then
  echo "✓ MySQL 已在 3306 运行"
elif [ "$WITH_MYSQL" -eq 1 ]; then
  echo "MySQL 未运行，尝试启动（可能要求输入 sudo 密码）……"
  sudo /usr/local/mysql/support-files/mysql.server start || {
    echo "MySQL 启动失败，请手动执行：sudo /usr/local/mysql/support-files/mysql.server start" >&2
    exit 1
  }
  wait_for_port 3306 "MySQL" 30
else
  echo "⚠ 3306 上没有 MySQL。数据库没起后端会连不上，可加 --mysql 参数自动启动。" >&2
fi

# ---------- 后端 ----------
if port_busy 8080; then
  echo "⚠ 8080 已被占用，跳过后端启动（先跑 ./scripts/stop-dev.sh）"
else
  echo "构建并启动后端……"
  (
    cd "$ROOT/backend"
    mvn -B -q -DskipTests -Dmaven.repo.local="${MAVEN_REPO:-$HOME/.m2/repository}" package
  )

  (
    cd "$ROOT/backend"
    DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
      nohup java -jar target/ct-platform-backend-1.0.0.jar > "$DEV/backend.log" 2>&1 &
    echo $! > "$DEV/backend.pid"
  )
  wait_for_port 8080 "后端" 90 || { tail -20 "$DEV/backend.log" >&2; exit 1; }
fi

# ---------- 自有前端 ----------
if port_busy 5174; then
  echo "⚠ 5174 已被占用，跳过自有前端"
else
  (
    cd "$ROOT/frontend"
    [ -d node_modules ] || npm install --no-audit --no-fund
    nohup ./node_modules/.bin/vite --port 5174 --strictPort > "$DEV/frontend.log" 2>&1 &
    echo $! > "$DEV/frontend.pid"
  )
  wait_for_port 5174 "frontend" 60
fi

# ---------- 主平台前端（可选） ----------
if [ "$WITH_PLATFORM" -eq 1 ]; then
  if [ ! -d "$PLATFORM_DIR/frontend" ]; then
    echo "⚠ 找不到 $PLATFORM_DIR/frontend，跳过主平台前端" >&2
  elif port_busy 5173; then
    echo "⚠ 5173 已被占用，跳过主平台前端"
  else
    (
      cd "$PLATFORM_DIR/frontend"
      [ -d node_modules ] || npm install --no-audit --no-fund
      nohup ./node_modules/.bin/vite --port 5173 --strictPort > "$DEV/platform.log" 2>&1 &
      echo $! > "$DEV/platform.pid"
    )
    wait_for_port 5173 "platform" 60
  fi
fi

echo
echo "启动完成："
echo "  自有项目   http://localhost:5174"
[ "$WITH_PLATFORM" -eq 1 ] && echo "  主平台     http://localhost:5173  →「人工智能」"
echo "  后端接口   http://localhost:8080/api/health"
echo "  日志目录   $DEV"
echo "  关闭项目   ./scripts/stop-dev.sh"
