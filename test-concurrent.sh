#!/bin/bash

# ============================================
# AI 客服系统 - 高并发测试脚本
# ============================================

echo "🚀 AI 客服系统高并发测试"
echo ""

# 配置
BASE_URL="http://localhost:8080"
ENDPOINT="/api/v1/chat"
CONCURRENT_USERS=${1:-10}
REQUESTS_PER_USER=${2:-5}
TOTAL_REQUESTS=$((CONCURRENT_USERS * REQUESTS_PER_USER))

echo "📊 测试配置:"
echo "  并发用户数: $CONCURRENT_USERS"
echo "  每用户请求数: $REQUESTS_PER_USER"
echo "  总请求数: $TOTAL_REQUESTS"
echo "  目标地址: $BASE_URL$ENDPOINT"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 记录开始时间
START_TIME=$(date +%s%N)

SUCCESS_COUNT=0
FAIL_COUNT=0
RESPONSE_TIMES=()

# 并发测试函数
run_concurrent_test() {
    local user_id=$1
    local request_num=$2
    
    local user="testuser_${user_id}"
    local session="session_${user_id}_${request_num}"
    local message="你好，请帮我测试并发场景，这是第 ${request_num} 个请求"
    
    local start=$(date +%s%N)
    
    local response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL$ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "{\"userId\":\"$user\",\"message\":\"$message\",\"sessionId\":\"$session\",\"multiTurn\":false}")
    
    local end=$(date +%s%N)
    local duration=$(( (end - start) / 1000000 )) # 毫秒
    
    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✅ 用户$user 请求$request_num 成功 (${duration}ms)${NC}"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        echo -e "${RED}❌ 用户$user 请求$request_num 失败 (HTTP $http_code, ${duration}ms)${NC}"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
    
    RESPONSE_TIMES+=($duration)
}

# 执行并发测试
echo "开始测试..."
echo "-----------------------------------"

# 使用后台进程模拟并发
for ((user=1; user<=CONCURRENT_USERS; user++)); do
    for ((req=1; req<=REQUESTS_PER_USER; req++)); do
        run_concurrent_test $user $req &
    done
done

# 等待所有后台进程完成
wait

# 计算统计信息
END_TIME=$(date +%s%N)
TOTAL_TIME=$(( (END_TIME - START_TIME) / 1000000 )) # 毫秒
AVG_TIME=$((TOTAL_TIME / TOTAL_REQUESTS))

# 计算成功率
SUCCESS_RATE=$((SUCCESS_COUNT * 100 / TOTAL_REQUESTS))
FAIL_RATE=$((FAIL_COUNT * 100 / TOTAL_REQUESTS))

echo ""
echo "-----------------------------------"
echo "📈 测试结果汇总"
echo "==================================="
echo "总请求数:     $TOTAL_REQUESTS"
echo "成功请求:     $SUCCESS_COUNT (${GREEN}${SUCCESS_RATE}%${NC})"
echo "失败请求:     $FAIL_COUNT (${RED}${FAIL_RATE}%${NC})"
echo "总耗时:       ${TOTAL_TIME}ms"
echo "平均响应时间: ${AVG_TIME}ms"
echo "吞吐量:       $((TOTAL_REQUESTS * 1000 / TOTAL_TIME)) 请求/秒"
echo "==================================="

# 检查限流
echo ""
echo "🔍 限流检查:"
curl -s "$BASE_URL/admin/ip/blacklist?ip=127.0.0.1" | grep -o "黑名单\|不在黑名单" || echo "  限流状态: 未触发"

# 检查缓存统计
echo ""
echo "💾 缓存统计:"
curl -s "$BASE_URL/admin/cache/stats" 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "  缓存统计: 暂时不可用"
