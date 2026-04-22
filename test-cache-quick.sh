#!/bin/bash

# ============================================
# AI 客服系统 - 快速缓存命中率测试
# ============================================

echo "💾 快速缓存命中率测试"
echo ""

BASE_URL="http://localhost:8080"
ENDPOINT="/api/v1/chat"

# 清空缓存
echo "🗑️ 清空缓存..."
curl -s -X POST "$BASE_URL/admin/cache/clear" > /dev/null 2>&1
echo ""

# 测试消息
MESSAGES=("如何查询订单？" "退款流程是什么？" "有什么优惠活动？")
TOTAL_HITS=0
TOTAL_MISSES=0

for msg in "${MESSAGES[@]}"; do
    echo "测试消息: $msg"
    echo "-----------------------------------"
    
    # 第一次请求 - 应该miss
    start=$(date +%s%N)
    resp1=$(curl -s -X POST "$BASE_URL$ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "{\"userId\":\"test\",\"message\":\"$msg\",\"sessionId\":\"s1\",\"multiTurn\":false}")
    end=$(date +%s%N)
    time1=$(( (end - start) / 1000000 ))
    cached1=$(echo "$resp1" | python3 -c "import sys, json; print(json.load(sys.stdin).get('cached', False))")
    
    if [ "$cached1" = "True" ]; then
        echo "  请求1: HIT (${time1}ms) [应该是MISS]"
    else
        echo "  请求1: MISS (${time1}ms) ✅"
        TOTAL_MISSES=$((TOTAL_MISSES + 1))
    fi
    
    # 第二次请求 - 应该HIT
    start=$(date +%s%N)
    resp2=$(curl -s -X POST "$BASE_URL$ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "{\"userId\":\"test\",\"message\":\"$msg\",\"sessionId\":\"s1\",\"multiTurn\":false}")
    end=$(date +%s%N)
    time2=$(( (end - start) / 1000000 ))
    cached2=$(echo "$resp2" | python3 -c "import sys, json; print(json.load(sys.stdin).get('cached', False))")
    
    if [ "$cached2" = "True" ]; then
        echo "  请求2: HIT (${time2}ms) ✅"
        TOTAL_HITS=$((TOTAL_HITS + 1))
    else
        echo "  请求2: MISS (${time2}ms) [应该是HIT]"
        TOTAL_MISSES=$((TOTAL_MISSES + 1))
    fi
    
    # 第三次请求 - 应该HIT
    start=$(date +%s%N)
    resp3=$(curl -s -X POST "$BASE_URL$ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "{\"userId\":\"test\",\"message\":\"$msg\",\"sessionId\":\"s1\",\"multiTurn\":false}")
    end=$(date +%s%N)
    time3=$(( (end - start) / 1000000 ))
    cached3=$(echo "$resp3" | python3 -c "import sys, json; print(json.load(sys.stdin).get('cached', False))")
    
    if [ "$cached3" = "True" ]; then
        echo "  请求3: HIT (${time3}ms) ✅"
        TOTAL_HITS=$((TOTAL_HITS + 1))
    else
        echo "  请求3: MISS (${time3}ms) [应该是HIT]"
        TOTAL_MISSES=$((TOTAL_MISSES + 1))
    fi
    
    echo ""
done

TOTAL=$((TOTAL_HITS + TOTAL_MISSES))
if [ $TOTAL -gt 0 ]; then
    HIT_RATE=$((TOTAL_HITS * 100 / TOTAL))
else
    HIT_RATE=0
fi

echo "==================================="
echo "📊 缓存测试结果汇总"
echo "==================================="
echo "总请求数: $TOTAL"
echo "缓存命中: $TOTAL_HITS"
echo "缓存未命中: $TOTAL_MISSES"
echo "命中率: ${HIT_RATE}%"
echo ""

if [ $HIT_RATE -ge 60 ]; then
    echo "✅ 缓存逻辑正常！"
elif [ $HIT_RATE -ge 30 ]; then
    echo "⚠️ 缓存部分命中，可能需要优化"
else
    echo "❌ 缓存命中率过低，需要排查"
fi

echo "==================================="
