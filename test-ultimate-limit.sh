#!/bin/bash

echo "========================================="
echo "AI Service 真实场景极限压力测试"
echo "========================================="
echo ""

BASE_URL="http://localhost:8080"

# 预热问题（缓存命中）
CACHED_QUESTIONS=(
    "如何查询订单？"
    "退款流程是什么？"
    "如何修改收货地址？"
    "有什么优惠活动？"
    "商品价格多少？"
    "发货时间多长？"
    "如何联系客服？"
    "如何申请售后？"
    "如何开具发票？"
    "如何取消订单？"
    "支持哪些支付方式？"
    "如何查看物流信息？"
    "商品有质量问题怎么办？"
    "如何修改账户信息？"
    "如何领取优惠券？"
)

# 检查服务状态
echo "🔍 检查服务状态..."
health=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null)
if [ "$health" != "200" ]; then
    echo "❌ 服务未运行，请先启动后端服务"
    exit 1
fi
echo "✅ 服务运行中"
echo ""

# 获取初始指标
echo "📊 初始系统指标:"
curl -s "$BASE_URL/actuator/prometheus" 2>/dev/null | grep "^ai_" | head -10
echo ""

# 测试函数 - 模拟真实用户行为
run_test() {
    local test_name=$1
    local total_requests=$2
    local cache_ratio=$3  # 缓存命中比例 (0-100)
    local tmp_dir=$(mktemp -d)
    
    echo "-----------------------------------------"
    echo "📊 测试: $test_name"
    echo "  总请求数: $total_requests"
    echo "  预期缓存命中率: ${cache_ratio}%"
    echo "-----------------------------------------"
    
    local start_time=$(date +%s%N)
    
    # 并发发送请求
    pids=()
    for i in $(seq 1 $total_requests); do
        (
            # 决定使用缓存问题还是新问题
            local rand=$((RANDOM % 100))
            local message=""
            
            if [ $rand -lt $cache_ratio ]; then
                local idx=$((RANDOM % ${#CACHED_QUESTIONS[@]}))
                message="${CACHED_QUESTIONS[$idx]}"
            else
                message="这是一个全新问题编号${i}，请详细回答并解释"
            fi
            
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
                -X POST "$BASE_URL/api/v1/chat" \
                -H "Content-Type: application/json" \
                -d "{\"userId\":\"user-$i\",\"message\":\"$message\"}" \
                --max-time 600 2>/dev/null)
            
            http_code=$(echo "$response" | sed -n '2p')
            time_total=$(echo "$response" | sed -n '3p')
            body=$(echo "$response" | sed -n '1p')
            
            is_cached="false"
            if echo "$body" | grep -q '"cached":true'; then
                is_cached="true"
            fi
            
            echo "$http_code,$time_total,$is_cached" >> "$tmp_dir/results.csv"
        ) &
        pids+=($!)
    done
    
    # 等待所有请求完成
    for pid in "${pids[@]}"; do
        wait $pid
    done
    
    local end_time=$(date +%s%N)
    local duration_ms=$(( (end_time - start_time) / 1000000 ))
    local duration_s=$(awk "BEGIN {printf \"%.1f\", $duration_ms / 1000}")
    
    # 统计结果
    if [ -f "$tmp_dir/results.csv" ]; then
        local total=$(wc -l < "$tmp_dir/results.csv")
        local success=$(awk -F',' '$1=="200"' "$tmp_dir/results.csv" | wc -l)
        local failed=$(awk -F',' '$1~/^5/' "$tmp_dir/results.csv" | wc -l)
        local rate_limited=$(awk -F',' '$1=="429"' "$tmp_dir/results.csv" | wc -l)
        local cache_hits=$(awk -F',' '$3=="true"' "$tmp_dir/results.csv" | wc -l)
        local cache_misses=$(awk -F',' '$3=="false"' "$tmp_dir/results.csv" | wc -l)
        
        # 计算响应时间统计
        local avg_time=$(awk -F',' '{sum+=$2; count++} END {if(count>0) printf "%.3f", sum/count; else print "0"}' "$tmp_dir/results.csv")
        local max_time=$(awk -F',' 'BEGIN{max=0} {if($2>max) max=$2} END{printf "%.3f", max}' "$tmp_dir/results.csv")
        local min_time=$(awk -F',' 'BEGIN{min=999} {if($2<min) min=$2} END{if(min<999) printf "%.3f", min; else print "0"}' "$tmp_dir/results.csv")
        
        # 缓存请求的平均时间
        local avg_cached_time=$(awk -F',' '$3=="true" {sum+=$2; count++} END {if(count>0) printf "%.3f", sum/count; else print "0"}' "$tmp_dir/results.csv")
        local avg_uncached_time=$(awk -F',' '$3=="false" {sum+=$2; count++} END {if(count>0) printf "%.3f", sum/count; else print "0"}' "$tmp_dir/results.csv")
        
        # TPS
        local tps="0"
        if [ $duration_ms -gt 0 ]; then
            tps=$(awk "BEGIN {printf \"%.1f\", $total * 1000 / $duration_ms}")
        fi
        
        # 成功率
        local success_rate="0"
        if [ $total -gt 0 ]; then
            success_rate=$(awk "BEGIN {printf \"%.1f\", $success * 100 / $total}")
        fi
        
        # 实际缓存命中率
        local actual_cache_rate="0"
        if [ $total -gt 0 ]; then
            actual_cache_rate=$(awk "BEGIN {printf \"%.1f\", $cache_hits * 100 / $total}")
        fi
        
        echo ""
        echo "📈 结果:"
        echo "  总请求数: $total"
        echo "  ✅ 成功(200): $success ($success_rate%)"
        echo "  🚫 限流(429): $rate_limited"
        echo "  ❌ 错误(5xx): $failed"
        echo "  💾 缓存命中: $cache_hits / 未命中: $cache_misses (实际命中率: $actual_cache_rate%)"
        echo "  ⏱️  平均响应: ${avg_time}s (最小: ${min_time}s, 最大: ${max_time}s)"
        echo "  ⚡ 缓存请求: ${avg_cached_time}s"
        echo "  🐢 未缓存请求: ${avg_uncached_time}s"
        echo "  🚀 TPS: $tps"
        echo "  🕐 总耗时: ${duration_s}s"
        echo ""
    fi
    
    rm -rf "$tmp_dir"
}

# 阶段1: 低并发 - 80%缓存命中 (正常业务场景)
run_test "阶段1: 低并发 (50请求, 80%缓存)" 50 80

# 阶段2: 中等并发 - 70%缓存命中 (活跃业务)
run_test "阶段2: 中并发 (100请求, 70%缓存)" 100 70

# 阶段3: 高并发 - 60%缓存命中 (促销场景)
run_test "阶段3: 高并发 (150请求, 60%缓存)" 150 60

# 阶段4: 超高并发 - 50%缓存命中 (高峰期)
run_test "阶段4: 超高并发 (200请求, 50%缓存)" 200 50

# 阶段5: 极限压力 - 40%缓存命中 (大促场景)
run_test "阶段5: 极限压力 (300请求, 40%缓存)" 300 40

# 阶段6: 极端压力 - 30%缓存命中 (系统极限测试)
run_test "阶段6: 极端压力 (500请求, 30%缓存)" 500 30

# 最终汇总
echo ""
echo "========================================="
echo "📊 最终系统指标"
echo "========================================="
curl -s "$BASE_URL/actuator/prometheus" 2>/dev/null | grep "^ai_"
echo ""

echo "✅ 真实场景极限压力测试完成!"
