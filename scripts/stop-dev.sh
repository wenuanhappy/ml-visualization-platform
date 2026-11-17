#!/usr/bin/env bash
#
# 关闭机器学习可视化平台的进程。
#
#   ./scripts/stop-dev.sh            # 关闭后端 + 自有前端 + 主平台前端
#   ./scripts/stop-dev.sh --mysql    # 顺便停掉项目自带的 MySQL 实例（.dev/mysql）
#
# 优先按 .dev/*.pid 精确关闭，PID 失效时退回按命令行特征匹配。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV="$ROOT/.dev"

WITH_MYSQL=0
for arg in "$@"; do
  case "$arg" in
    --mysql) WITH_MYSQL=1 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

kill_pidfile() {
  local name="$1"
  local file="$DEV/$name.pid"
  [ -f "$file" ] || return 0
  local pid
  pid="$(cat "$file")"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 10); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.5
    done
    kill -9 "$pid" 2>/dev/null || true
    echo "  ✓ 已停止 ${name}（PID ${pid}）"
  else
    echo "  · $name 未在运行"
  fi
  rm -f "$file"
}

echo "停止项目进程……"
kill_pidfile backend
kill_pidfile frontend
kill_pidfile platform

# 兜底：PID 文件丢失或进程换了 PID 的情况
pkill -f "ct-platform-backend-1.0.0.jar" 2>/dev/null && echo "  ✓ 已清理残留后端进程" || true
pkill -f "vite --port 5174" 2>/dev/null && echo "  ✓ 已清理残留自有前端" || true
pkill -f "vite --port 5173" 2>/dev/null && echo "  ✓ 已清理残留主平台前端" || true

if [ "$WITH_MYSQL" -eq 1 ]; then
  bash "$ROOT/scripts/db.sh" stop
else
  echo "  · 项目自带数据库仍在运行（要一起停：./scripts/stop-dev.sh --mysql）"
fi

# 给进程一点优雅退出的时间（Spring Boot 关闭要几秒）
for _ in $(seq 1 20); do
  remaining=""
for port in 8080 5173 5174; do
  if nc -z -G 1 127.0.0.1 "$port" >/dev/null 2>&1; then
    remaining="$remaining $port"
  fi
done
  [ -z "$remaining" ] && break
  sleep 0.5
done

echo
remaining=""
for port in 8080 5173 5174; do
  if nc -z -G 1 127.0.0.1 "$port" >/dev/null 2>&1; then
    remaining="$remaining $port"
  fi
done
if [ -n "$remaining" ]; then
  echo "⚠ 仍有端口在监听："
  echo "$remaining"
else
  echo "全部已停止（8080 / 5173 / 5174 均无监听）"
fi
