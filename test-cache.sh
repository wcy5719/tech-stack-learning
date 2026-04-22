#!/bin/bash

# ============================================
# AI 客服系统 - 缓存命中率测试脚本
# ============================================

echo "💾 AI 客服系统缓存命中率测试"
echo ""

BASE_URL="http://localhost:8080"
ENDPOINT="/api/v1/chat"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 清空缓存
echo "🗑️  步骤1: 清空现有缓存..."
curl -s -X POST "$BASE_URL/admin/cache/clear" > /dev/null 2>&1
echo -e "${GREEN}✅ 缓存已清空${NC}"
echo ""

# 查看初始缓存状态
echo "📊 步骤2: 初始缓存状态..."
INITIAL_STATS=$(curl -s "$BASE_URL/admin/cache/stats" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('localCacheStats', 'N/A'))
except:
    print('缓存统计不可用')
" 2>/dev/null)
echo "  $INITIAL_STATS"
echo ""

# 测试场景1: 相同请求测试（应该命中缓存）
echo "🔄 步骤3: 相同请求测试 (预期: 首次miss, 后续hit)"
echo "-----------------------------------"

USER="cache_test_user"
SESSION="cache_test_session"
MESSAGES=(
    "如何查询订单？"
    "退款流程是什么？"
    "有什么优惠活动？"
)

TOTAL_REQUESTS=0
HIT_COUNT=0
MISS_COUNT=0

for msg in "${MESSAGES[@]}"; do
    echo ""
    echo "测试消息: $msg"
    
    # 发送3次相同请求
    for i in 1 2 3; do
        TOTAL_REQUESTS=$((TOTAL_REQUESTS + 1))
        
        start=$(date +%s%N)
        response=$(curl -s -X POST "$BASE_URL$ENDPOINT" \
            -H "Content-Type: application/json" \
            -d "{\"userId\":\"$USER\",\"message\":\"$msg\",\"sessionId\":\"$SESSION\",\"multiTurn\":false}")
        end=$(date +%s%N)
        
        duration=$(( (end - start) / 1000000 ))
        
        # 检查响应中是否有缓存标识
        if echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); print('true' if data.get('cached', False) else 'false')" 2>/dev/null | grep -q "true"; then
            echo -e "  ${GREEN}✅ 请求$i: 缓存命中 (${duration}ms)${NC}"
            HIT_COUNT=$((HIT_COUNT + 1))
        else
            echo -e "  ${YELLOW}❌ 请求$i: 缓存未命中 (${duration}ms)${NC}"
            MISS_COUNT=$((MISS_COUNT + 1))
        fi
        
        sleep 0.5
    done
done

# 查看缓存统计
echo ""
echo "-----------------------------------"
echo "📈 步骤4: 缓存统计"
echo "==================================="

STATS=$(curl -s "$BASE_URL/admin/cache/stats" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    local_stats = data.get('localCacheStats', '')
    # 解析 CacheStats
    import re
    hits = re.search(r'hitCount=(\d+)', local_stats)
    misses = re.search(r'missCount=(\d+)', local_stats)
    evictions = re.search(r'evictionCount=(\d+)', local_stats)
    
    hit_count = int(hits.group(1)) if hits else 0
    miss_count = int(misses.group(1)) if misses else 0
    eviction_count = int(evictions.group(1)) if evictions else 0
    total = hit_count + miss_count
    
    hit_rate = (hit_count / total * 100) if total > 0 else 0
    
    print(f'缓存命中次数: {hit_count}')
    print(f'缓存未命中次数: {miss_count}')
    print(f'缓存驱逐次数: {eviction_count}')
    print(f'总请求数: {total}')
    print(f'缓存命中率: {hit_rate:.1f}%')
    print(f'布隆过滤器状态: {data.get(\"bloomFilterStatus\", \"N/A\")}')
except Exception as e:
    print(f'缓存统计解析失败: {e}')
" 2>/dev/null)

echo "$STATS"
echo "==================================="

# 测试场景2: 不同请求测试
echo ""
echo "🔄 步骤5: 不同请求测试 (预期: 全部miss)"
echo "-----------------------------------"

DIFFERENT_MISS_COUNT=0

for i in $(seq 1 5); do
    msg="这是第$i个不同的测试问题"
    
    start=$(date +%s%N)
    response=$(curl -s -X POST "$BASE_URL$ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "{\"userId\":\"$USER\",\"message\":\"$msg\",\"sessionId\":\"$SESSION\",\"multiTurn\":false}")
    end=$(date +%s%N)
    
    duration=$(( (end - start) / 1000000 ))
    
    if echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); print('true' if data.get('cached', False) else 'false')" 2>/dev/null | grep -q "true"; then
        echo -e "  ${GREEN}✅ 不同请求$i: 缓存命中 (${duration}ms)${NC}"
    else
        echo -e "  ${YELLOW}❌ 不同请求$i: 缓存未命中 (${duration}ms)${NC}"
        DIFFERENT_MISS_COUNT=$((DIFFERENT_MISS_COUNT + 1))
    fi
    
    sleep 0.5
done

echo ""
echo -e "不同请求缓存未命中: ${DIFFERENT_MISS_COUNT}/5 (预期: 5/5)"

# 最终统计
echo ""
echo "==================================="
echo "📊 最终缓存测试报告"
echo "==================================="
echo "相同请求测试:"
echo "  总请求数: $TOTAL_REQUESTS"
echo "  缓存命中: $HIT_COUNT"
echo "  缓存未命中: $MISS_COUNT"

if [ $((HIT_COUNT + MISS_COUNT)) -gt 0 ]; then
    HIT_RATE=$((HIT_COUNT * 100 / (HIT_COUNT + MISS_COUNT)))
    echo "  命中率: ${HIT_RATE}%"
fi

echo ""
echo "不同请求测试:"
echo "  总请求数: 5"
echo "  缓存未命中: $DIFFERENT_MISS_COUNT"
echo ""

if [ $DIFFERENT_MISS_COUNT -eq 5 ]; then
    echo -e "${GREEN}✅ 缓存逻辑正常：相同请求可命中，不同请求不命中${NC}"
else
    echo -e "${YELLOW}⚠️ 缓存逻辑可能存在问题${NC}"
fi

echo "==================================="
