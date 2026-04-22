#!/bin/bash

echo "========================================="
echo "AI Service 高并发压力测试"
echo "========================================="
echo ""

BASE_URL="http://localhost:8080"

# 检查服务是否运行
echo "检查服务状态..."
health=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null)
if [ "$health" != "200" ]; then
    echo "❌ 服务未运行，请先启动后端服务"
    exit 1
fi
echo "✅ 服务运行中"
echo ""

# 测试函数 - 发送N个并发请求并统计
run_concurrency_test() {
    local concurrency=$1
    local test_name=$2
    local tmp_dir=$(mktemp -d)
    
    echo "-----------------------------------------"
    echo "📊 测试: $test_name ($concurrency 并发)"
    echo "-----------------------------------------"
    
    local start_time=$(date +%s%N)
    
    # 并发发送请求
    pids=()
    for i in $(seq 1 $concurrency); do
        (
            response=$(curl -s -w "\n%{http_code}" \
                -X POST "$BASE_URL/api/v1/chat" \
                -H "Content-Type: application/json" \
                -d "{\"userId\":\"stress-user-$i\",\"message\":\"如何查询订单？\"}" \
                --max-time 10 2>/dev/null)
            
            http_code=$(echo "$response" | tail -1)
            body=$(echo "$response" | sed '$d')
            
            echo "$http_code" >> "$tmp_dir/codes.txt"
            echo "$body" >> "$tmp_dir/bodies.txt"
        ) &
        pids+=($!)
    done
    
    # 等待所有请求完成
    for pid in "${pids[@]}"; do
        wait $pid
    done
    
    local end_time=$(date +%s%N)
    local duration_ms=$(( (end_time - start_time) / 1000000 ))
    
    # 统计结果
    if [ -f "$tmp_dir/codes.txt" ]; then
        local total=$(wc -l < "$tmp_dir/codes.txt")
        local success=$(grep -c "200" "$tmp_dir/codes.txt" 2>/dev/null || echo 0)
        local rate_limited=$(grep -c "429" "$tmp_dir/codes.txt" 2>/dev/null || echo 0)
        local errors=$(grep -c "50[0-9]" "$tmp_dir/codes.txt" 2>/dev/null || echo 0)
        local cache_hits=$(grep -c '"cached":true' "$tmp_dir/bodies.txt" 2>/dev/null || echo 0)
        local cache_misses=$(grep -c '"cached":false' "$tmp_dir/bodies.txt" 2>/dev/null || echo 0)
        
        # 计算TPS
        local tps="0"
        if [ $duration_ms -gt 0 ]; then
            tps=$(echo "scale=1; $total * 1000 / $duration_ms" | bc)
        fi
        
        # 计算成功率
        local success_rate="0"
        if [ $total -gt 0 ]; then
            success_rate=$(echo "scale=1; $success * 100 / $total" | bc)
        fi
        
        # 计算缓存命中率
        local cache_hit_rate="0"
        local total_cache=$((cache_hits + cache_misses))
        if [ $total_cache -gt 0 ]; then
            cache_hit_rate=$(awk "BEGIN {printf \"%.1f\", $cache_hits * 100 / $total_cache}")
        fi
        
        echo ""
        echo "📈 结果:"
        echo "  总请求数: $total"
        echo "  ✅ 成功(200): $success ($success_rate%)"
        echo "  🚫 限流(429): $rate_limited"
        echo "  ❌ 错误(5xx): $errors"
        echo "  💾 缓存命中: $cache_hits (命中率: $cache_hit_rate%)"
        echo "  ⏱️  总耗时: ${duration_ms}ms"
        echo "  🚀 TPS: $tps"
        echo ""
    fi
    
    rm -rf "$tmp_dir"
}

# 获取初始Metrics
echo "📊 初始系统指标:"
initial_cache_hits=$(curl -s "$BASE_URL/actuator/prometheus" 2>/dev/null | grep "ai_cache_hits_total" | grep -v "#" | awk '{print $2}' || echo "0")
initial_requests=$(curl -s "$BASE_URL/actuator/prometheus" 2>/dev/null | grep "ai_requests_total" | grep -v "#" | awk '{print $2}' || echo "0")
echo "  缓存命中数: $initial_cache_hits"
echo "  总请求数: $initial_requests"
echo ""

# 阶段1: 10并发
run_concurrency_test 10 "低并发"

# 阶段2: 50并发
run_concurrency_test 50 "中等并发"

# 阶段3: 100并发
run_concurrency_test 100 "高并发"

# 阶段4: 200并发（Tomcat最大线程数）
run_concurrency_test 200 "极限并发(Tomcat上限)"

# 阶段5: 500并发（超出Tomcat容量）
run_concurrency_test 500 "超极限并发"

# 最终汇总
echo ""
echo "========================================="
echo "📊 最终系统指标"
echo "========================================="
curl -s "$BASE_URL/actuator/prometheus" 2>/dev/null | grep "^ai_" | head -20
echo ""

echo "✅ 压力测试完成!"
