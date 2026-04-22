#!/bin/bash

# ============================================
# AI 智能客服系统 - 一键启动脚本
# ============================================

echo "🚀 启动 AI 智能客服系统..."
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 检查 vLLM 是否运行
echo -e "${YELLOW}📡 检查 vLLM 服务状态...${NC}"
if curl -s http://localhost:8000/v1/models > /dev/null 2>&1; then
    echo -e "${GREEN}✅ vLLM 服务已运行 (http://localhost:8000)${NC}"
else
    echo -e "${YELLOW}⚠️ vLLM 服务未运行，正在启动...${NC}"
    echo -e "${YELLOW}📦 使用模型: Qwen3-Coder-30B-A3B-Instruct-MLX-4bit${NC}"
    echo ""
    echo "请在新终端窗口执行以下命令启动 vLLM:"
    echo "  source ~/.venv-vllm/bin/activate"
    echo "  vllm-mlx serve /Users/WangChenYang/.lmstudio/models/lmstudio-community/Qwen3-Coder-30B-A3B-Instruct-MLX-4bit --port 8000 --host 0.0.0.0 --max-tokens 32768 --max-num-seqs 16 --gpu-memory-utilization 0.95"
    echo ""
    read -p "按回车键继续启动后端..."
fi

echo ""
echo -e "${YELLOW}🔧 启动 Spring Boot 后端...${NC}"
cd "$(dirname "$0")"
./mvnw spring-boot:run &
BACKEND_PID=$!

echo -e "${GREEN}✅ 后端已启动 (PID: $BACKEND_PID)${NC}"
sleep 5

echo ""
echo -e "${YELLOW}🎨 启动前端开发服务器...${NC}"
cd frontend
npm run dev &
FRONTEND_PID=$!

echo -e "${GREEN}✅ 前端已启动 (PID: $FRONTEND_PID)${NC}"

echo ""
echo -e "${GREEN}===========================================${NC}"
echo -e "${GREEN}✅ AI 智能客服系统已启动!${NC}"
echo -e "${GREEN}===========================================${NC}"
echo ""
echo -e "🌐 前端地址: ${GREEN}http://localhost:3000${NC}"
echo -e "🔌 后端地址: ${GREEN}http://localhost:8080${NC}"
echo -e "🤖 vLLM 地址: ${GREEN}http://localhost:8000${NC}"
echo ""
echo -e "${YELLOW}按 Ctrl+C 停止所有服务...${NC}"

# 等待用户中断
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; echo -e '\n${RED}✅ 服务已停止${NC}'; exit" INT

wait
