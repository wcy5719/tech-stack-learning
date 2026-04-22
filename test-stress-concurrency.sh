#!/bin/bash

echo "========================================="
echo "AI Service 高并发压力测试"
echo "========================================="
echo ""

BASE_URL="http://localhost:8080"
TOTAL_TESTS=0
SUCCESS_COUNT=0
FAIL_COUNT=0
TIMEOUT_COUNT=0
CACHE_HIT_COUNT=0
RATE_LIMIT_COUNT=0

# 测试函数
run_test() {
    local concurrency=$1
    local duration=$2
    local total_requests=0
    local success=0
    local fail=0
    local timeout=0
    local cache_hit=0
    local rate_limited=0
    
    echo ""
    echo "-----------------------------------------"
    echo "测试: 并发数=$concurrency, 持续时间=${duration}s"
    echo "-----------------------------------------"
    
    # 使用ab或curl进行并发测试
    local start_time=$(date +%s)
    
    # 创建临时目录存储结果
    local tmp_dir=$(mktemp -d)
    
    # 并发发送请求
    for i in $(seq 1 $concurrency); do
        (
            local req_count=0
            local end_time=$(($(date +%s) + duration))
            while [ $(date +%s) -lt $end_time ]; do
                local response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
                    -X POST "$BASE_URL/api/v1/chat" \
                    -H "Content-Type: application/json" \
                    -d "{\"userId\":\"stress-test-$i\",\"message\":\"如何查询订单？\"}" \
                    --max-time 5 2>/dev/null)
                
                local http_code=$(echo "$response" | tail -2 | head -1)
                local time_total=$(echo "$response" | tail -1)
                local body=$(echo "$response" | head -n -2)
                
                echo "$http_code,$time_total,$body" >> "$tmp_dir/results_$i.csv"
                req_count=$((req_count + 1))
            done
            echo "线程$i完成: $req_count个请求" >> "$tmp_dir/thread_stats.txt"
        ) &
    done
    
    # 等待所有线程完成
    wait
    
    local end_time=$(date +%s)
    local actual_duration=$((end_time - start_time))
    
    # 统计结果
    if [ -d "$tmp_dir" ]; then
        # 合并所有结果
        cat "$tmp_dir"/*.csv 2>/dev/null > "$tmp_dir/all_results.csv"
        
        total_requests=$(wc -l < "$tmp_dir/all_results.csv" 2>/dev/null || echo 0)
        success=$(grep -c "^200," "$tmp_dir/all_results.csv" 2>/dev/null || echo 0)
        fail=$(grep -c "^50[0-9]," "$tmp_dir/all_results.csv" 2>/dev/null || echo 0)
        timeout=$(grep -c "^000," "$tmp_dir/all_results.csv" 2>/dev/null || echo 0)
        rate_limited=$(grep -c "^429," "$tmp_dir/all_results.csv" 2>/dev/null || echo 0)
        
        # 检查缓存命中
        cache_hit=$(grep -c "\"cached\":true" "$tmp_dir/all_results.csv" 2>/dev/null || echo 0)
        
        # 计算平均响应时间
        local avg_time=$(awk -F',' '{sum+=$2; count++} END {if(count>0) printf "%.3f", sum/count; else print "0"}' "$tmp_dir/all_results.csv")
        
        # 计算TPS
        local tps=0
        if [ $actual_duration -gt 0 ]; then
            tps=$(echo "scale=1; $total_requests / $actual_duration" | bc)
        fi
    fi
    
    # 输出结果
    echo ""
    echo "结果汇总:"
    echo "  总请求数: $total_requests"
    echo "  成功(200): $success"
    echo "  失败(5xx): $fail"
    echo "  超时: $timeout"
    echo "  限流(429): $rate_limited"
    echo "  缓存命中: $cache_hit"
    echo "  实际耗时: ${actual_duration}s"
    echo "  平均响应时间: ${avg_time}s"
    echo "  TPS: $tps"
    echo ""
    
    # 清理临时文件
    rm -rf "$tmp_dir"
    
    # 返回结果（通过全局变量）
    TOTAL_TESTS=$((TOTAL_TESTS + total_requests))
    SUCCESS_COUNT=$((SUCCESS_COUNT + success))
    FAIL_COUNT=$((FAIL_COUNT + fail))
    TIMEOUT_COUNT=$((TIMEOUT_COUNT + timeout))
    CACHE_HIT_COUNT=$((CACHE_HIT_COUNT + cache_hit))
    RATE_LIMIT_COUNT=$((RATE_LIMIT_COUNT + rate_limited))
}

# 获取初始指标
echo "初始系统状态:"
curl -s "$BASE_URL/actuator/health" | python3 -m json.tool 2>/dev/null || echo "健康检查失败"
echo ""

# 阶段1: 低并发测试 (10并发)
run_test 10 5

# 阶段2: 中等并发测试 (50并发)
run_test 50 10

# 阶段3: 高并发测试 (100并发)
run_test 100 10

# 阶段4: 超高并发测试 (200并发)
run_test 200 10

# 阶段5: 极限并发测试 (500并发)
run_test 500 10

# 最终汇总
echo ""
echo "========================================="
echo "最终测试结果汇总"
echo "========================================="
echo "总请求数: $TOTAL_TESTS"
echo "成功: $SUCCESS_COUNT"
echo "失败: $FAIL_COUNT"
echo "超时: $TIMEOUT_COUNT"
echo "限流: $RATE_LIMIT_COUNT"
echo "缓存命中: $CACHE_HIT_COUNT"
echo ""

# 获取最终Prometheus指标
echo "最终系统指标:"
curl -s "$BASE_URL/actuator/prometheus" | grep "^ai_" | head -15
echo ""

echo "测试完成!"
