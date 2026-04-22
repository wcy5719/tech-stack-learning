#!/bin/bash

# ============================================
# AI 客服系统 - 渐进式压力测试（阶段3和阶段4）
# ============================================

echo "🚀 AI 客服系统渐进式压力测试"
echo ""

BASE_URL="http://localhost:8080"
ENDPOINT="/api/v1/chat"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 测试函数
run_test() {
    local concurrent_users=$1
    local requests_per_user=$2
    local delay=$3
    local stage_name=$4
    
    echo ""
    echo -e "${BLUE}===================================${NC}"
    echo -e "${BLUE}📊 $stage_name${NC}"
    echo -e "${BLUE}===================================${NC}"
    echo "并发用户数: $concurrent_users"
    echo "每用户请求数: $requests_per_user"
    echo "请求间隔: ${delay}s"
    echo "总请求数: $((concurrent_users * requests_per_user))"
    echo ""
    
    # 检查后端是否运行
    if ! curl -s "$BASE_URL/health" > /dev/null 2>&1; then
        echo -e "${RED}❌ 后端未运行，测试终止${NC}"
        return 1
    fi
    echo -e "${GREEN}✅ 后端运行正常${NC}"
    echo ""
    
    START_TIME=$(date +%s%N)
    
    SUCCESS_COUNT=0
    FAIL_COUNT=0
    RESPONSE_TIMES=()
    
    # 分批发射请求
    for batch in $(seq 1 $requests_per_user); do
        echo -e "${YELLOW}批次 $batch / $requests_per_user${NC}"
        
        for user in $(seq 1 $concurrent_users); do
            (
                local user_id="testuser_st${concurrent_users}_u${user}"
                local session="session_st${concurrent_users}_${user}"
                local message="压力测试并发${concurrent_users}用户${user}批次${batch}"
                
                local start=$(date +%s%N)
                local response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL$ENDPOINT" \
                    -H "Content-Type: application/json" \
                    -d "{\"userId\":\"$user_id\",\"message\":\"$message\",\"sessionId\":\"$session\",\"multiTurn\":false}" \
                    --max-time 30 2>/dev/null)
                local end=$(date +%s%N)
                local duration=$(( (end - start) / 1000000 ))
                local http_code=$(echo "$response" | tail -1)
                
                if [ "$http_code" = "200" ]; then
                    echo -e "${GREEN}✅ 用户${user} 成功 (${duration}ms)${NC}"
                    echo "SUCCESS" >> /tmp/test_success_$$.txt
                else
                    echo -e "${RED}❌ 用户${user} 失败 HTTP:$http_code (${duration}ms)${NC}"
                    echo "FAIL" >> /tmp/test_fail_$$.txt
                fi
            ) &
        done
        
        # 等待当前批次完成
        wait
        
        # 批次间延迟
        if [ "$delay" -gt 0 ]; then
            sleep $delay
        fi
    done
    
    END_TIME=$(date +%s%N)
    TOTAL_TIME=$(( (END_TIME - START_TIME) / 1000000 ))
    
    SUCCESS_COUNT=$(cat /tmp/test_success_$$.txt 2>/dev/null | wc -l)
    FAIL_COUNT=$(cat /tmp/test_fail_$$.txt 2>/dev/null | wc -l)
    TOTAL_REQUESTS=$((SUCCESS_COUNT + FAIL_COUNT))
    
    # 清理临时文件
    rm -f /tmp/test_success_$$.txt /tmp/test_fail_$$.txt
    
    # 计算统计
    if [ $TOTAL_REQUESTS -gt 0 ]; then
        SUCCESS_RATE=$((SUCCESS_COUNT * 100 / TOTAL_REQUESTS))
        FAIL_RATE=$((FAIL_COUNT * 100 / TOTAL_REQUESTS))
        AVG_TIME=$((TOTAL_TIME / TOTAL_REQUESTS))
        THROUGHPUT=$((TOTAL_REQUESTS * 1000 / TOTAL_TIME))
    else
        SUCCESS_RATE=0
        FAIL_RATE=0
        AVG_TIME=0
        THROUGHPUT=0
    fi
    
    echo ""
    echo -e "${BLUE}===================================${NC}"
    echo -e "${BLUE}📈 测试结果 - $stage_name${NC}"
    echo -e "${BLUE}===================================${NC}"
    echo "总请求数:     $TOTAL_REQUESTS"
    echo -e "成功请求:     $SUCCESS_COUNT ${GREEN}(${SUCCESS_RATE}%)${NC}"
    echo -e "失败请求:     $FAIL_COUNT ${RED}(${FAIL_RATE}%)${NC}"
    echo "总耗时:       ${TOTAL_TIME}ms ($((TOTAL_TIME / 1000))s)"
    echo "平均响应时间: ${AVG_TIME}ms"
    echo "吞吐量:       $THROUGHPUT 请求/秒"
    echo -e "${BLUE}===================================${NC}"
}

# 清理临时文件
rm -f /tmp/test_success_$$.txt /tmp/test_fail_$$.txt

# 检查后端
echo "🔍 检查后端状态..."
if curl -s "$BASE_URL/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 后端运行正常${NC}"
else
    echo -e "${RED}❌ 后端未运行！${NC}"
    echo ""
    echo "请先启动后端服务:"
    echo "  cd /Users/WangChenYang/IdeaProjects/实战项目/AIService"
    echo "  DB_PASSWORD=A20060307wcy ./mvnw spring-boot:run"
    exit 1
fi

# 阶段1: 5 并发（基准测试）
run_test 5 2 2 "阶段1：基准测试 (5并发)"

# 阶段2: 20 并发（轻度压力）
run_test 20 2 3 "阶段2：轻度压力 (20并发)"

# 阶段3: 50 并发（中度压力）
run_test 50 2 5 "阶段3：中度压力 (50并发)"

# 阶段4: 100 并发（重度压力）
run_test 100 2 8 "阶段4：重度压力 (100并发)"

# 最终系统状态检查
echo ""
echo "==================================="
echo "📊 测试完成 - 系统状态检查"
echo "==================================="

if curl -s "$BASE_URL/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ 后端仍然运行正常${NC}"
    curl -s "$BASE_URL/health" | python3 -m json.tool 2>/dev/null
else
    echo -e "${RED}❌ 后端已崩溃或停止运行${NC}"
fi

echo ""
echo "💾 缓存统计:"
curl -s "$BASE_URL/admin/cache/stats" 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('localCacheStats', 'N/A'))
except:
    print('缓存统计不可用')
" 2>/dev/null

echo ""
echo "✅ 压力测试完成！"
