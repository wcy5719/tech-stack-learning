# ============================================
# vLLM-MLX 本地大模型配置
# ============================================

# 🤖 模型信息
# 1. Qwen3-Coder-30B (主模型)
#    路径: /Users/WangChenYang/.lmstudio/models/lmstudio-community/Qwen3-Coder-30B-A3B-Instruct-MLX-4bit
#    特点: 编程专用，MLX 4bit 量化
#
# 2. Qwen3.5-27B-Claude-4.6-Opus-Distilled
#    路径: /Users/WangChenYang/.lmstudio/models/mlx-community/Qwen3.5-27B-Claude-4.6-Opus-Distilled-MLX-4bit
#    特点: Claude 蒸馏版，通用对话
#
# 3. Devstral-Small-2-24B
#    路径: /Users/WangChenYang/.lmstudio/models/mlx-community/Devstral-Small-2-24B-Instruct-2512-4bit
#    特点: 轻量级，纯文本对话

# 📝 可用别名
# vllm-max     - 极限性能版 (max-tokens: 32768, gpu-memory: 95%)
# vllm-safe    - 安全版 (max-tokens: 8192, gpu-memory: 85%)
# vllm-qwen35  - Qwen3.5-27B Claude 蒸馏版
# vllm-devstral - Devstral-24B 轻量版
# vllm-stop    - 停止 vLLM 服务
# vllm-test    - 测试 vLLM 服务
# vllm-logs    - 查看 vLLM 日志
# ai-start     - 一键启动全部 (vLLM + Open WebUI)
# ai-stop      - 一键停止全部
# ai-status    - 查看全部状态

# 🔌 环境变量
# OPENAI_API_BASE=http://localhost:8080/v1
# OPENAI_API_KEY=sk-no-key
