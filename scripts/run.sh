#!/bin/bash
#
# 同时启动 Spring Boot 后端 + Vite 前端（自动释放端口）
# Usage:
#   ./scripts/run.sh            # kill 旧进程后启动
#   ./scripts/run.sh --kill     # 仅 kill 不启动
#   ./scripts/run.sh --skip     # 不检查端口，直接启动
#   ./scripts/run.sh --backend  # 仅启动后端（不启动前端）
#   ./scripts/run.sh --frontend # 仅启动前端（不启动后端）
#

set -euo pipefail

cd "$(dirname "$0")/.."
PROJECT_DIR="$(pwd)"
BACKEND_PORT="${SPRING_BOOT_PORT:-8080}"
FRONTEND_PORT="${VITE_PORT:-5173}"

# ── 颜色 ──────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ── 是否本项目相关进程（node / java），用于避免误杀无关程序 ──────
is_project_process() {
  local pid="$1" comm
  comm="$(ps -p "$pid" -o comm= 2>/dev/null || true)"
  case "$comm" in
    *node*|*java*|*vite*|*pnpm*|*mvn*) return 0 ;;
    *) return 1 ;;
  esac
}

# ── 查找占用端口的 PID（仅 LISTEN 状态） ─────────────
find_pids() {
  local port="$1"
  if command -v lsof &>/dev/null; then
    # 必须带 -sTCP:LISTEN：否则 lsof -i tcp:PORT 会把「作为客户端连接过该端口」
    # 或「连着任意主机同端口」的无关进程也列出来，进而被下面误杀（如微信内置浏览器）
    lsof -nP -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null || true
  elif command -v ss &>/dev/null; then
    ss -tlnp "sport = :${port}" 2>/dev/null \
      | awk 'NR>1{print $NF}' \
      | grep -oP '(?<=pid=)\d+' || true
  else
    fuser "${port}/tcp" 2>/dev/null | xargs || true
  fi
}

# ── kill 进程 ──────────────────────────────────────
kill_port() {
  local port="$1" label="$2"
  local pid comm pids killed remaining
  pids="$(find_pids "$port")"

  if [[ -z "$pids" ]]; then
    info "[${label}] 端口 ${port} 未被占用"
    return 0
  fi

  warn "[${label}] 端口 ${port} 被以下进程占用:"
  for pid in $pids; do
    comm="$(ps -p "$pid" -o comm= 2>/dev/null || true)"
    if [[ -n "$comm" ]]; then
      echo "  PID $pid  $comm"
    else
      echo "  PID $pid (已消失)"
    fi
  done

  # 只终止本项目相关进程（node/java），其余仅提示不处理，防止误杀（如微信）
  killed=0
  for pid in $pids; do
    if is_project_process "$pid"; then
      kill "$pid" 2>/dev/null || true
      killed=1
    else
      comm="$(ps -p "$pid" -o comm= 2>/dev/null || true)"
      warn "[${label}] 跳过非本项目进程 PID $pid (${comm:-未知})，未终止"
    fi
  done

  if [[ "$killed" -eq 0 ]]; then
    warn "[${label}] 端口 ${port} 上无可释放的本项目进程，跳过"
    return 0
  fi

  for i in $(seq 1 10); do
    remaining="$(find_pids "$port")"
    [[ -z "$remaining" ]] && { ok "[${label}] 端口 ${port} 已释放"; return 0; }
    sleep 1
  done

  warn "强制终止端口 ${port} 的进程..."
  for pid in $pids; do
    if is_project_process "$pid"; then
      kill -9 "$pid" 2>/dev/null || true
    fi
  done
  sleep 1
  ok "[${label}] 端口 ${port} 已释放（强制）"
}

# ── 清理退出 ───────────────────────────────────────
cleanup() {
  echo ""
  info "收到退出信号，正在清理..."
  local child_pid=""
  if [[ -n "${FRONTEND_PID:-}" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    info "停止前端 (PID $FRONTEND_PID)..."
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi
  exit 0
}
trap cleanup SIGINT SIGTERM

# ── 启动前端（后台） ──────────────────────────────────
start_frontend() {
  echo ""
  info "启动前端 (Vite)..."
  echo "  cd ${PROJECT_DIR}/frontend"
  echo "  pnpm dev"
  echo ""

  cd "$PROJECT_DIR/frontend"
  pnpm dev &
  FRONTEND_PID=$!
  ok "前端已启动 (PID $FRONTEND_PID) → http://localhost:${FRONTEND_PORT}"
  cd "$PROJECT_DIR"
}

# ── 启动后端（前台） ──────────────────────────────────
start_backend() {
  echo ""
  info "启动后端 (Spring Boot)..."
  echo "  cd ${PROJECT_DIR}"
  echo "  ./mvnw spring-boot:run"
  echo ""

  cd "$PROJECT_DIR"
  exec ./mvnw spring-boot:run
}

# ── 主流程 ─────────────────────────────────────────
main() {
  local action="${1:-}"

  case "$action" in
    --kill|-k)
      kill_port "$BACKEND_PORT"  "后端"
      kill_port "$FRONTEND_PORT" "前端"
      exit 0
      ;;
    --skip|-s)
      info "跳过端口检查，直接启动..."
      start_frontend
      start_backend
      ;;
    --backend|-b)
      info "仅启动后端..."
      kill_port "$BACKEND_PORT" "后端"
      start_backend
      ;;
    --frontend|-f)
      info "仅启动前端..."
      kill_port "$FRONTEND_PORT" "前端"
      cd "$PROJECT_DIR/frontend"
      exec pnpm dev
      ;;
    --help|-h)
      echo "用法: $0 [选项]"
      echo ""
      echo "选项:"
      echo "  (无参数)    kill 旧进程后同时启动前后端"
      echo "  --kill      仅释放端口，不启动"
      echo "  --skip      不检查端口，直接启动"
      echo "  --backend   仅启动后端"
      echo "  --frontend  仅启动前端"
      echo "  --help      显示此帮助"
      echo ""
      echo "环境变量:"
      echo "  SPRING_BOOT_PORT=8080  后端端口"
      echo "  VITE_PORT=5173         前端端口"
      exit 0
      ;;
    ""|--*)
      kill_port "$BACKEND_PORT"  "后端"
      kill_port "$FRONTEND_PORT" "前端"
      start_frontend
      start_backend
      ;;
    *)
      err "未知参数: $action"
      echo "用法: $0 [--kill|--skip|--backend|--frontend|--help]"
      exit 1
      ;;
  esac
}

main "$@"