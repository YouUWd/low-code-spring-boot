#!/bin/bash

# ==================== 部署脚本 ====================
# 用途: 管理 jdec-platform-app 应用的启动、停止、重启和状态查询
# 使用: ./deploy.sh {start|stop|restart|status} [env]
#       env 可选值: dev, test, prod (默认: prod)
# 示例:
#   ./deploy.sh start dev      # 以开发环境启动
#   ./deploy.sh restart test   # 以测试环境重启
#   ./deploy.sh stop           # 停止应用
#   ./deploy.sh status         # 查看应用状态
# ==================== 配置区 ====================
APP_NAME="jdec-platform-app"
APP_DIR="/home/admin/application/jdec-platform-app/target"
JAR_NAME="jdec-platform-app-1.0.0-SNAPSHOT.jar"
JAR_FILE="${APP_DIR}/${JAR_NAME}"
LOG_DIR="${APP_DIR}/logs"
PID_FILE="${APP_DIR}/${APP_NAME}.pid"
APP_PORT=5600

# JVM & Spring 配置
JVM_OPTS="-server -Xms512m -Xmx1g -XX:+UseG1GC"
SPRING_OPTS="--server.port=${APP_PORT}"
HEALTH_CHECK_URL="http://localhost:${APP_PORT}/actuator/health"
MAX_WAIT_SECONDS=120 # 最大等待时间

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }

# ==================== 工具函数 ====================

# 检查进程是否存活 (使用 kill -0 比 ps 更可靠)
is_running() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

# 检查端口是否被占用
check_port() {
    (netstat -tlnp 2>/dev/null | grep -q ":${APP_PORT} ") || (ss -tlnp 2>/dev/null | grep -q ":${APP_PORT} ")
}

# ==================== 核心动作 ====================

stop_application() {
    log_info "正在停止应用..."
    if ! is_running; then
        log_info "应用未运行"
        rm -f "$PID_FILE"
        return 0
    fi

    local pid=$(cat "$PID_FILE")
    log_info "发送停止信号到进程 (PID: $pid)..."
    kill "$pid"

    local count=0
    while kill -0 "$pid" 2>/dev/null; do
        sleep 1
        ((count++))
        if [ $count -gt 30 ]; then
            log_warning "正常停止超时，执行强制杀进程 (SIGKILL)..."
            kill -9 "$pid"
            break
        fi
        echo -n "."
    done
    rm -f "$PID_FILE"
    echo ""
    log_success "应用已停止"
}

start_application() {
    local env="${1:-test}"
    log_info "准备启动应用 (环境: $env)..."
    echo ""

    # 1. 前置检查
    if is_running; then
        log_error "启动失败：应用已在运行 (PID: $(cat $PID_FILE))"
        log_info "请先执行: ./deploy.sh stop 或 ./deploy.sh restart"
        return 1
    fi

    if check_port; then
        log_error "启动失败：端口 ${APP_PORT} 已被占用"
        log_info "请检查是否有其他进程占用该端口，或修改 APP_PORT 配置"
        return 1
    fi

    # 2. 启动进程
    log_info "启动参数:"
    log_info "  - JAR 文件: $JAR_FILE"
    log_info "  - 环境: $env"
    log_info "  - 端口: $APP_PORT"
    log_info "  - JVM 内存: 512M ~ 1G"
    log_info "  - 日志文件: ${LOG_DIR}/application.log"
    echo ""

    cd "$APP_DIR" || exit 1
    mkdir -p "$LOG_DIR"
    nohup java $JVM_OPTS -Dspring.profiles.active="$env" -jar "$JAR_FILE" $SPRING_OPTS >> "${LOG_DIR}/application.log" 2>&1 &

    local pid=$!
    echo $pid > "$PID_FILE"
    log_info "进程已启动 (PID: $pid)"
    log_info "等待应用就绪 (最多 ${MAX_WAIT_SECONDS} 秒)..."
    echo ""

    # 3. 循环探测 (核心优化点)
    local start_time=$(date +%s)
    local dot_count=0
    while true; do
        # 检查进程是否意外退出 (快速失败)
        if ! kill -0 "$pid" 2>/dev/null; then
            echo ""
            log_error "启动失败：进程已异常退出"
            log_info "请查看日志了解详情: tail -f ${LOG_DIR}/application.log"
            rm -f "$PID_FILE"
            return 1
        fi

        # 检查健康检查接口 (2秒超时)
        local http_code=$(curl -s -m 2 -o /dev/null -w "%{http_code}" "$HEALTH_CHECK_URL" || echo "000")
        if [ "$http_code" == "200" ]; then
            echo ""
            log_success "启动成功！服务已就绪 (HTTP 200)"
            log_info "访问地址: http://localhost:${APP_PORT}"
            log_info "健康检查: $HEALTH_CHECK_URL"
            return 0
        fi

        # 检查总时长是否超时
        local current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        if [ $elapsed -ge $MAX_WAIT_SECONDS ]; then
            echo ""
            log_error "启动超时：在 ${MAX_WAIT_SECONDS} 秒内未获得响应"
            log_warning "应用可能仍在启动中，请稍候后手动检查"
            log_info "查看日志: tail -f ${LOG_DIR}/application.log"
            log_info "检查状态: ./deploy.sh status"
            return 1
        fi

        echo -n "."
        ((dot_count++))
        if [ $((dot_count % 30)) -eq 0 ]; then
            echo " (已等待 $elapsed 秒)"
        fi
        sleep 2
    done
}

# ==================== 主逻辑 ====================

ACTION=$1
ENV=${2:-prod}

# 显示欢迎信息
show_usage() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}          jdec-platform-app 部署脚本 v1.0                 ${BLUE}║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo "用法: $0 {start|stop|restart|status} [env]"
    echo ""
    echo "命令说明:"
    echo "  start   - 启动应用 (默认环境: prod)"
    echo "  stop    - 停止应用"
    echo "  restart - 重启应用 (默认环境: prod)"
    echo "  status  - 查看应用运行状态"
    echo ""
    echo "环境参数 (可选):"
    echo "  dev     - 开发环境"
    echo "  test    - 测试环境"
    echo "  prod    - 生产环境 (默认)"
    echo ""
    echo "使用示例:"
    echo "  $0 start dev       # 以开发环境启动"
    echo "  $0 restart test    # 以测试环境重启"
    echo "  $0 stop            # 停止应用"
    echo "  $0 status          # 查看状态"
    echo ""
}

case "$ACTION" in
    start)
        start_application "$ENV"
        exit $?
        ;;
    stop)
        stop_application
        exit $?
        ;;
    restart)
        log_info "执行重启操作..."
        stop_application
        sleep 2
        start_application "$ENV"
        exit $?
        ;;
    status)
        echo ""
        if is_running; then
            local pid=$(cat "$PID_FILE")
            log_success "应用运行中 (PID: $pid)"
            log_info "访问地址: http://localhost:${APP_PORT}"
        else
            log_error "应用未运行"
        fi
        echo ""
        ;;
    *)
        show_usage
        exit 1
        ;;
esac