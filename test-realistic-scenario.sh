#!/bin/bash

echo "========================================="
echo "AI Service 真实场景混合压力测试"
echo "========================================="
echo ""

BASE_URL="http://localhost:8080"

# 缓存问题列表（会命中缓存）
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
)

# 新问题列表（不会命中缓存，需要vLLM推理）
NEW_QUESTIONS=(
    "我昨天下的订单什么时候能到上海？"
    "我想把收货地址从北京改到深圳怎么办？"
    "你们的会员有什么特殊权益吗？"
    "iPhone15Pro现在有什么折扣活动？"
    "我买的东西已经发货三天了还没到正常吗？"
    "如果收到的商品破损了怎么处理？"
    "可以帮我查一下订单号为202403150001的物流吗？"
    "我想开具公司抬头的发票需要什么信息？"
    "你们的7天无理由退货政策具体是怎么规定的？"
    "可以用花呗分期付款吗？"
    "我想取消刚刚下错的那个订单怎么操作？"
    "这个商品有保修服务吗保修期多久？"
    "你们支持哪些国际快递服务？"
    "我想批量采购有批发价吗？"
    "收到的商品和描述不符可以退货吗？"
)

# 检查服务是否运行
echo "🔍 检查服务状态..."
health=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null)
if [ "$health" != "200" ]; then
    echo "❌ 服务未运行，请先启动后端服务"
    exit 1
fi
echo "✅ 服务运行中"
echo ""

# 测试函数 - 模拟真实用户行为
run_mixed_test() {
    local test_round=$1
    local total_users=$2
    local cache_hit_ratio=$3  # 缓存命中率预期（0-100）
    local tmp_dir=$(mktemp -d)
    
    echo "-----------------------------------------"
    echo "📊 第${test_round}轮测试"
    echo "  模拟用户数: $total_users"
    echo "  预期缓存命中率: ${cache_hit_ratio}%"
    echo "-----------------------------------------"
    
    local start_time=$(date +%s%N)
    
    # 并发发送请求
    pids=()
    for i in $(seq 1 $total_users); do
        (
            # 根据比例决定使用缓存问题还是新问题
            local rand=$((RANDOM % 100))
            local message=""
            
            if [ $rand -lt $cache_hit_ratio ]; then
                # 使用缓存问题
                local idx=$((RANDOM % ${#CACHED_QUESTIONS[@]}))
                message="${CACHED_QUESTIONS[$idx]}"
            else
                # 使用新问题（需要vLLM推理）
                local idx=$((RANDOM % ${#NEW_QUESTIONS[@]}))
                message="${NEW_QUESTIONS[$idx]}"
            fi
            
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
                -X POST "$BASE_URL/api/v1/chat" \
                -H "Content-Type: application/json" \
                -d "{\"userId\":\"user-$i\",\"message\":\"$message\"}" \
                --max-time 35 2>/dev/null)
            
            http_code=$(echo "$response" | sed -n '2p')
            time_total=$(echo "$response" | sed -n '3p')
            body=$(echo "$response" | sed -n '1p')
            
            # 判断是否命中缓存
            is_cached="false"
            if echo "$body" | grep -q '"cached":true'; then
                is_cached="true"
            fi
            
            echo "$http_code,$time_total,$is_cached,$body" >> "$tmp_dir/results.csv"
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
        local timeout=$(awk -F',' '$1=="000"' "$tmp_dir/results.csv" | wc -l)
        local cache_hits=$(awk -F',' '$3=="true"' "$tmp_dir/results.csv" | wc -l)
        local cache_misses=$(awk -F',' '$3=="false"' "$tmp_dir/results.csv" | wc -l)
        
        # 计算平均响应时间
        local avg_time=$(awk -F',' '{sum+=$2; count++} END {if(count>0) printf "%.3f", sum/count; else print "0"}' "$tmp_dir/results.csv")
        
        # 缓存命中请求的平均响应时间
        local avg_cached_time=$(awk -F',' '$3=="true" {sum+=$2; count++} END {if(count>0) printf "%.3f", sum/count; else print "0"}' "$tmp_dir/results.csv")
        
        # 缓存未命中请求的平均响应时间
        local avg_uncached_time=$(awk -F',' '$3=="false" {sum+=$2; count++} END {if(count>0) printf "%.3f", sum/count; else print "0"}' "$tmp_dir/results.csv")
        
        # 计算TPS
        local tps="0"
        if [ $duration_ms -gt 0 ]; then
            tps=$(awk "BEGIN {printf \"%.1f\", $total * 1000 / $duration_ms}")
        fi
        
        # 计算成功率
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
        echo "  ⏰ 超时: $timeout"
        echo "  💾 缓存命中: $cache_hits / 缓存未命中: $cache_misses"
        echo "  📊 实际缓存命中率: $actual_cache_rate%"
        echo "  ⏱️  平均响应时间: ${avg_time}s"
        echo "  ⚡ 缓存请求平均耗时: ${avg_cached_time}s"
        echo "  🐢 未缓存请求平均耗时: ${avg_uncached_time}s"
        echo "  🚀 TPS: $tps"
        echo "  🕐 总耗时: ${duration_s}s"
        echo ""
    fi
    
    rm -rf "$tmp_dir"
}

# 获取初始指标
echo "📊 初始系统指标:"
curl -s "$BASE_URL/actuator/prometheus" 2>/dev/null | grep "^ai_" | head -10
echo ""

# 第1轮：80%缓存命中（典型日常使用场景）
run_mixed_test 1 50 80

# 第2轮：50%缓存命中（促销活动期间）
run_mixed_test 2 50 50

# 第3轮：20%缓存命中（大量新问题场景）
run_mixed_test 3 30 20

# 第4轮：高并发混合场景
run_mixed_test 4 100 70

# 第5轮：极限压力测试
run_mixed_test 5 150 60

# 最终汇总
echo ""
echo "========================================="
echo "📊 最终系统指标"
echo "========================================="
curl -s "$BASE_URL/actuator/prometheus" 2>/dev/null | grep "^ai_"
echo ""

echo "✅ 真实场景混合压力测试完成!"
