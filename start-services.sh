#!/bin/bash
# AI Platform 一键启动脚本 (Linux/macOS/Git Bash)
# 启动 Redis、Chroma向量库、BGEM3模型服务

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    echo "用法: $0 [选项]"
    echo "选项:"
    echo "  --skip-redis       跳过 Redis"
    echo "  --skip-chroma      跳过 Chroma"
    echo "  --skip-embedding   跳过 Embedding 服务"
    echo "  -h, --help         显示帮助"
}

SKIP_REDIS=false
SKIP_CHROMA=false
SKIP_EMBEDDING=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-redis) SKIP_REDIS=true; shift ;;
        --skip-chroma) SKIP_CHROMA=true; shift ;;
        --skip-embedding) SKIP_EMBEDDING=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "未知选项: $1"; usage; exit 1 ;;
    esac
done

check_port() {
    if command -v lsof &> /dev/null; then
        lsof -i ":$1" &> /dev/null
    elif command -v netstat &> /dev/null; then
        netstat -tuln 2>/dev/null | grep -q ":$1 "
    else
        # 默认认为端口可用
        return 1
    fi
}

start_redis() {
    echo -e "${CYAN}=== 启动 Redis ===${NC}"

    if check_port 6379; then
        echo -e "${YELLOW}Redis 已在运行 (端口6379)${NC}"
        return 0
    fi

    if command -v redis-server &> /dev/null; then
        redis-server --daemonize yes --port 6379
        sleep 2
        if check_port 6379; then
            echo -e "${GREEN}Redis 启动成功 (端口6379)${NC}"
            return 0
        fi
    fi

    echo -e "${RED}Redis 未安装或启动失败，请手动启动${NC}"
    return 1
}

start_chroma() {
    echo -e "${CYAN}=== 启动 Chroma 向量库 ===${NC}"

    if check_port 9000; then
        echo -e "${YELLOW}Chroma 已在运行 (端口9000)${NC}"
        return 0
    fi

    # 尝试使用 Docker
    if command -v docker &> /dev/null; then
        if docker ps | grep -q chroma; then
            echo -e "${YELLOW}Chroma容器已在运行${NC}"
            return 0
        fi

        echo -e "${YELLOW}尝试使用 Docker 启动 Chroma...${NC}"
        if docker run -d --name chroma -p 9000:8000 chromadb/chroma 2>/dev/null; then
            sleep 5
            if check_port 9000 || docker ps | grep -q chroma; then
                echo -e "${GREEN}Chroma(Docker) 启动成功 (端口9000)${NC}"
                return 0
            fi
        fi
    fi

    # 尝试使用 Python
    if command -v python3 &> /dev/null; then
        echo -e "${YELLOW}尝试使用 Python 启动 Chroma...${NC}"
        # 后台启动 Chroma
        nohup python3 -m chromadb --host localhost --port 9000 > "$SCRIPT_DIR/logs/chroma.log" 2>&1 &
        sleep 3
        if check_port 9000; then
            echo -e "${GREEN}Chroma 启动成功 (端口9000)${NC}"
            return 0
        fi
    fi

    echo -e "${RED}Chroma 启动失败，请安装 Docker 或运行: pip install chromadb${NC}"
    return 1
}

start_embedding() {
    echo -e "${CYAN}=== 启动 BGEM3 Embedding 服务 ===${NC}"

    if check_port 8000; then
        echo -e "${YELLOW}Embedding服务已在运行 (端口8000)${NC}"
        return 0
    fi

    VENV_PYTHON="$SCRIPT_DIR/embedding-service/.venv/bin/python"
    if [[ ! -f "$VENV_PYTHON" ]]; then
        # 尝试 Windows 路径
        VENV_PYTHON="$SCRIPT_DIR/embedding-service/.venv/Scripts/python.exe"
    fi

    if [[ ! -f "$VENV_PYTHON" ]]; then
        echo -e "${RED}Python虚拟环境未找到${NC}"
        echo -e "${YELLOW}请先运行以下命令创建虚拟环境:${NC}"
        echo "  cd $SCRIPT_DIR/embedding-service"
        echo "  python -m venv .venv"
        echo "  .venv/bin/pip install -r requirements.txt"
        return 1
    fi

    # 创建日志目录
    mkdir -p "$SCRIPT_DIR/logs"

    # 设置环境变量
    export BGE_M3_MODEL_PATH="${BGE_M3_MODEL_PATH:-BAAI/bge-m3}"
    export BGE_M3_DEVICE="${BGE_M3_DEVICE:-cpu}"

    echo -e "${YELLOW}正在启动BGEM3模型服务...${NC}"
    echo -e "${YELLOW}日志文件: $SCRIPT_DIR/logs/embedding-service.log${NC}"

    # 后台启动
    nohup "$VENV_PYTHON" -m uvicorn app.main:app --host 0.0.0.0 --port 8000 \
        > "$SCRIPT_DIR/logs/embedding-service.log" 2>&1 &
    EMBEDDING_PID=$!

    # 等待服务启动
    for i in {1..30}; do
        sleep 2
        if check_port 8000; then
            echo -e "${GREEN}BGEM3 Embedding服务启动成功 (端口8000)${NC}"
            return 0
        fi
    done

    echo -e "${RED}BGEM3 Embedding服务启动超时，请检查日志: $SCRIPT_DIR/logs/embedding-service.log${NC}"
    return 1
}

# 主流程
echo -e "========================================"
echo -e "   AI Platform 服务启动脚本"
echo -e "   Redis: 6379 | Chroma: 9000 | Embedding: 8000"
echo -e "========================================"

results=()

if [[ "$SKIP_REDIS" == "false" ]]; then
    start_redis && results+=("Redis:OK") || results+=("Redis:FAIL")
fi

if [[ "$SKIP_CHROMA" == "false" ]]; then
    start_chroma && results+=("Chroma:OK") || results+=("Chroma:FAIL")
fi

if [[ "$SKIP_EMBEDDING" == "false" ]]; then
    start_embedding && results+=("Embedding:OK") || results+=("Embedding:FAIL")
fi

# 服务状态检查
echo ""
echo -e "========================================"
echo -e "   服务状态检查"
echo -e "========================================"

for port in 6379 9000 8000; do
    name=""
    case $port in
        6379) name="Redis" ;;
        9000) name="Chroma" ;;
        8000) name="Embedding" ;;
    esac

    if check_port $port; then
        echo -e "[$name] ${GREEN}运行中${NC} (端口$port)"
    else
        echo -e "[$name] ${RED}未运行${NC} (端口$port)"
    fi
done

echo ""
echo -e "${CYAN}可用API地址:${NC}"
echo -e "  Redis:     ${WHITE}redis://localhost:6379${NC}"
echo -e "  Chroma:    ${WHITE}http://localhost:9000${NC}"
echo -e "  Embedding: ${WHITE}http://localhost:8000${NC}"
echo -e "  Backend:   ${WHITE}http://localhost:8080${NC}"
