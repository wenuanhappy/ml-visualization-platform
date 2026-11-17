#!/usr/bin/env bash
#
# 项目自带的 MySQL 实例（无需 sudo、无需 root 密码）。
#
#   ./scripts/db.sh ensure   # 初始化（首次）+ 启动 + 建库建号 + 导入 schema
#   ./scripts/db.sh start    # 只启动
#   ./scripts/db.sh stop     # 停止
#   ./scripts/db.sh status   # 查看状态
#   ./scripts/db.sh client   # 用 ct_app 打开一个 mysql 客户端
#
# 数据目录：.dev/mysql/data（已 gitignore），端口默认 3308（可用 DB_PORT 覆盖）。
# 这样就不会和系统里 /usr/local/mysql 那个需要 sudo 的实例冲突。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV="$ROOT/.dev"
DB_DIR="$DEV/mysql"
DATA="$DB_DIR/data"
SOCK="$DB_DIR/mysql.sock"
PIDFILE="$DB_DIR/mysqld.pid"
LOGFILE="$DB_DIR/mysqld.log"
BASEDIR="/usr/local/mysql"
MYSQLD="$BASEDIR/bin/mysqld"
MYSQL="$BASEDIR/bin/mysql"
MYSQLADMIN="$BASEDIR/bin/mysqladmin"
MYSQLDUMP="$BASEDIR/bin/mysqldump"

# shellcheck disable=SC1091
[ -f "$DEV/env" ] && source "$DEV/env"
PORT="${DB_PORT:-3308}"
DB_NAME="ct_platform"

mkdir -p "$DB_DIR"

running() {
  [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null
}

wait_ready() {
  for _ in $(seq 1 60); do
    if "$MYSQLADMIN" --socket="$SOCK" -u root ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.5
  done
  echo "MySQL 启动超时，日志：$LOGFILE" >&2
  tail -20 "$LOGFILE" >&2 || true
  return 1
}

init_data() {
  if [ -d "$DATA/mysql" ]; then
    return 0
  fi
  echo "初始化项目数据库目录（${DATA}）……"
  mkdir -p "$DATA"
  "$MYSQLD" --no-defaults --initialize-insecure \
    --basedir="$BASEDIR" --datadir="$DATA" --log-error="$LOGFILE"
  echo "  ✓ 初始化完成（root 为空密码，仅本机 socket 可连）"
}

start_server() {
  if running; then
    return 0
  fi
  init_data
  echo "启动项目数据库（端口 ${PORT}）……"
  nohup "$MYSQLD" --no-defaults \
    --basedir="$BASEDIR" --datadir="$DATA" \
    --port="$PORT" --bind-address=127.0.0.1 \
    --socket="$SOCK" --pid-file="$PIDFILE" --log-error="$LOGFILE" \
    --mysqlx=OFF --tmpdir="$DB_DIR" >/dev/null 2>&1 &
  wait_ready
  echo "  ✓ 已启动（socket: ${SOCK}）"
}

ensure_schema() {
  local exists
  exists="$("$MYSQL" --socket="$SOCK" -u root -N -B -e "SHOW DATABASES LIKE '${DB_NAME}'" 2>/dev/null || true)"
  if [ -z "$exists" ]; then
    echo "创建数据库与账号……"
    "$MYSQL" --socket="$SOCK" -u root < "$ROOT/backend/src/main/resources/schema.sql"
  fi
  # 账号幂等创建（密码来自 .dev/env）
  "$MYSQL" --socket="$SOCK" -u root -e "
    CREATE USER IF NOT EXISTS '${DB_USERNAME:-ct_app}'@'%' IDENTIFIED BY '${DB_PASSWORD:?需要 DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USERNAME:-ct_app}'@'%';
    FLUSH PRIVILEGES;" 2>/dev/null || true

}

# 旧库升级（必须在「建库」和「迁移」之后执行，否则表还不存在）
apply_upgrades() {
  "$MYSQL" --socket="$SOCK" -u root -D "$DB_NAME" -e "
    ALTER TABLE ml_dataset MODIFY COLUMN algorithm VARCHAR(128) NOT NULL COMMENT '适用算法，逗号分隔';" 2>/dev/null || true
}

# 从系统 3306 实例迁移一次数据（若能连上且目标库还没有数据集）
migrate_from_system() {
  local system_user="${SYSTEM_DB_USER:-${DB_USERNAME:-ct_app}}"
  local system_password="${SYSTEM_DB_PASSWORD:-${DB_PASSWORD:-}}"
  local has_data
  has_data="$("$MYSQL" --socket="$SOCK" -u root -D "$DB_NAME" -N -B -e "SELECT COUNT(*) FROM ml_dataset" 2>/dev/null || echo 0)"
  if [ "${has_data:-0}" -gt 0 ]; then
    return 0
  fi
  if ! nc -z -G 1 127.0.0.1 3306 >/dev/null 2>&1; then
    return 0
  fi
  echo "检测到 3306 上有旧实例，尝试迁移 ct_platform 数据……"
  if "$MYSQLDUMP" -h 127.0.0.1 -P 3306 -u "$system_user" ${system_password:+-p"$system_password"} \
      --single-transaction --skip-add-locks --no-tablespaces "$DB_NAME" 2>/dev/null \
      | "$MYSQL" --socket="$SOCK" -u root "$DB_NAME" 2>/dev/null; then
    echo "  ✓ 数据已迁移"
  else
    echo "  · 迁移失败或无需迁移，稍后由后端启动时自动填充数据集"
  fi
}

case "${1:-ensure}" in
  ensure)
    start_server
    ensure_schema
    migrate_from_system
    apply_upgrades
    echo "✓ 项目数据库就绪：127.0.0.1:${PORT}/${DB_NAME}（账号 ${DB_USERNAME:-ct_app}）"
    ;;
  start) start_server ;;
  stop)
    if running; then
      "$MYSQLADMIN" --socket="$SOCK" -u root shutdown >/dev/null 2>&1 || kill "$(cat "$PIDFILE")" 2>/dev/null || true
      echo "  ✓ 已停止项目数据库"
    else
      echo "  · 项目数据库未在运行"
    fi
    rm -f "$PIDFILE"
    ;;
  status)
    if running; then
      echo "项目数据库运行中：127.0.0.1:${PORT}（socket: ${SOCK}）"
    else
      echo "项目数据库未运行"
    fi
    ;;
  client)
    exec "$MYSQL" --socket="$SOCK" -u "${DB_USERNAME:-ct_app}" ${DB_PASSWORD:+-p"$DB_PASSWORD"} "$DB_NAME"
    ;;
  *)
    echo "用法: $0 {ensure|start|stop|status|client}" >&2
    exit 2
    ;;
esac
