#!/bin/bash

# ============================================
# 测试本地 vLLM-MLX 模型连接
# ============================================

echo "🧪 测试本地 vLLM 模型服务..."
echo ""

# 检查 vLLM 是否运行
echo "📡 连接测试: http://localhost:8000/v1/models"
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/v1/models 2>&1)

if [ "$RESPONSE" = "200" ]; then
    echo "✅ vLLM 服务正常运行!"
    echo ""
    echo "📦 可用模型:"
    curl -s http://localhost:8000/v1/models | python3 -m json.tool
    echo ""
    echo "🧪 发送测试请求..."
    curl -s http://localhost:8000/v1/chat/completions \
      -H "Content-Type: application/json" \
      -d '{
        "model": "Qwen3-Coder-30B-A3B-Instruct-MLX-4bit",
        "messages": [{"role": "user", "content": "你好，请简单介绍一下自己"}],
        "max_tokens": 100,
        "temperature": 0.7
      }' | python3 -m json.tool
else
    echo "❌ vLLM 服务未运行 (HTTP $RESPONSE)"
    echo ""
    echo "请执行以下命令启动 vLLM:"
    echo ""
    echo "  # 使用主模型 (Qwen3-Coder-30B)"
    echo "  vllm-max"
    echo ""
    echo "  或"
    echo ""
    echo "  # 使用安全模式"
    echo "  vllm-safe"
fi
