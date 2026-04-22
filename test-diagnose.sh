#!/bin/bash

# ============================================
# AI 客服系统 - 限流与超时问题诊断
# ============================================

echo "🔍 AI 客服系统限流与超时问题诊断"
echo ""

BASE_URL="http://localhost:8080"
ENDPOINT="/api/v1/chat"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 1. 检查限流配置
echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}1️⃣  限流配置检查${NC}"
echo -e "${BLUE}=======================================${NC}"

echo "查看 application.yml 配置:"
grep -A 3 "rate-limit:" /Users/WangChenYang/IdeaProjects/实战项目/AIService/src/main/resources/application.yml 2>/dev/null | grep "requests-per-minute" | sed 's/^/  /'

echo ""
echo "当前限流阈值: 100 请求/分钟"
echo "限流实现: Redis 计数器（固定窗口）"
echo ""

# 检查当前限流计数
CLIENT_IP=$(curl -s "$BASE_URL/health" > /dev/null && echo "127.0.0.1" || echo "unknown")
CURRENT_COUNT=$(redis-cli GET "rate_limit:ip:127.0.0.1" 2>/dev/null)

echo "客户端IP: 127.0.0.1"
echo "当前请求计数: ${CURRENT_COUNT:-0}"
echo ""

if [ "${CURRENT_COUNT:-0}" -gt 80 ] 2>/dev/null; then
    echo -e "${RED}⚠️  限流计数接近阈值！这会导致后续请求被拒绝${NC}"
elif [ "${CURRENT_COUNT:-0}" -gt 0 ] 2>/dev/null; then
    echo -e "${YELLOW}⚠️  限流计数: ${CURRENT_COUNT}/100${NC}"
else
    echo -e "${GREEN}✅ 限流计数正常${NC}"
fi

echo ""

# 2. 限流逻辑问题分析
echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}2️⃣  限流逻辑问题分析${NC}"
echo -e "${BLUE}=======================================${NC}"

echo -e "${CYAN}问题发现:${NC}"
echo ""
echo "❌ 问题1: 所有请求共享同一IP"
echo "   - 压力测试中所有请求来自 localhost (127.0.0.1)"
echo "   - 50并发 × 2请求 = 100请求瞬间到达"
echo "   - 超过100请求/分钟的阈值，触发限流"
echo ""
echo "❌ 问题2: 固定窗口限流算法"
echo "   - 使用 Redis INCR + EXPIRE(60s)"
echo "   - 窗口重置前所有请求共享计数器"
echo "   - 没有按用户ID限流，只按IP限流"
echo ""
echo "❌ 问题3: 白名单配置不当"
echo "   - 本地白名单: 127.0.0.1, 192.168.1.1"
echo "   - 但实际请求IP可能不是127.0.0.1"
echo ""

echo -e "${CYAN}修复建议:${NC}"
echo "  1. 测试环境增加限流阈值到 1000"
echo "  2. 实现按用户ID限流（而非仅IP）"
echo "  3. 使用滑动窗口算法替代固定窗口"
echo ""

# 3. 超时问题分析
echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}3️⃣  超时问题分析${NC}"
echo -e "${BLUE}=======================================${NC}"

echo -e "${CYAN}vLLM推理耗时测试:${NC}"
echo ""

echo "发送单个请求测试响应时间..."
START=$(date +%s%N)
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL$ENDPOINT" \
    -H "Content-Type: application/json" \
    -d '{"userId":"diag_test","message":"你好","sessionId":"diag_session","multiTurn":false}' \
    --max-time 60 2>/dev/null)
END=$(date +%s%N)

DURATION=$(( (END - START) / 1000000 ))
HTTP_CODE=$(echo "$RESP" | tail -1)

echo "  HTTP状态码: $HTTP_CODE"
echo "  响应时间: ${DURATION}ms ($(( DURATION / 1000 ))s)"
echo ""

if [ $DURATION -gt 10000 ]; then
    echo -e "${RED}❌ vLLM推理耗时过长 (>10秒)${NC}"
    echo "   - 这会导致Tomcat线程被长时间占用"
    echo "   - 并发请求时线程池迅速耗尽"
    echo "   - 后续请求排队等待或超时"
elif [ $DURATION -gt 5000 ]; then
    echo -e "${YELLOW}⚠️  vLLM推理耗时较长 (5-10秒)${NC}"
else
    echo -e "${GREEN}✅ vLLM推理耗时正常 (<5秒)${NC}"
fi

echo ""

# 4. Tomcat配置检查
echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}4️⃣  Tomcat线程池配置${NC}"
echo -e "${BLUE}=======================================${NC}"

echo "当前配置:"
grep -A 5 "tomcat:" /Users/WangChenYang/IdeaProjects/实战项目/AIService/src/main/resources/application.yml 2>/dev/null | sed 's/^/  /'

echo ""
echo "默认Tomcat配置:"
echo "  max-threads: 200 (最大线程数)"
echo "  max-connections: 10000 (最大连接数)"
echo "  accept-count: 100 (等待队列长度)"
echo "  connection-timeout: 30000ms (30秒)"
echo ""

if [ $DURATION -gt 30000 ]; then
    echo -e "${RED}❌ vLLM响应超过30秒超时阈值${NC}"
    echo "   建议增加 connection-timeout 或实现异步处理"
fi

echo ""

# 5. 综合问题分析
echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}5️⃣  综合问题分析${NC}"
echo -e "${BLUE}=======================================${NC}"

echo ""
echo -e "${RED}🔴 根本原因总结:${NC}"
echo ""
echo "阶段3/4测试失败的两个主要原因:"
echo ""
echo "1. ${RED}限流触发 (HTTP 429)${NC}"
echo "   - 100请求/分钟阈值太低"
echo "   - 50并发 × 2请求 = 100请求瞬间到达"
echo "   - 超过阈值后所有请求返回429"
echo "   - 日志: \"IP限流触发: ip=127.0.0.1, 当前请求数=100+\""
echo ""
echo "2. ${RED}vLLM推理超时 (HTTP 000)${NC}"
echo "   - 单次vLLM推理耗时: 5-40秒"
echo "   - Tomcat同步处理模式"
echo "   - 200个线程被长时间占用"
echo "   - 后续请求等待超时或连接被拒绝"
echo ""

echo -e "${GREEN}✅ 解决方案:${NC}"
echo ""
echo "方案A: 优化限流配置（快速）"
echo "  1. 测试环境提高限流阈值"
echo "  2. 实现按用户ID限流"
echo "  3. 使用令牌桶算法"
echo ""
echo "方案B: 优化vLLM调用（推荐）"
echo "  1. 实现异步处理（@Async）"
echo "  2. 使用WebSocket推送结果"
echo "  3. 配置合理的超时时间"
echo ""
echo "方案C: 增加缓存命中率（根本解决）"
echo "  1. 缓存常见问题答案"
echo "  2. 启动时预热缓存"
echo "  3. 减少对vLLM的直接调用"
echo ""

echo "==================================="
echo "✅ 诊断完成！"
echo "==================================="
