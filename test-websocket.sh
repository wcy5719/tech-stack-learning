#!/bin/bash

# ============================================
# WebSocket 并发测试脚本
# ============================================

echo "🚀 WebSocket 并发测试"
echo ""

WS_URL="ws://localhost:8080/ws/chat"
CONCURRENT_USERS=${1:-10}
REQUESTS_PER_USER=${2:-3}

echo "📊 测试配置:"
echo "  并发连接数: $CONCURRENT_USERS"
echo "  每连接消息数: $REQUESTS_PER_USER"
echo "  WebSocket地址: $WS_URL"
echo ""

# 使用 Python 进行 WebSocket 测试
python3 << 'PYTHON_SCRIPT'
import asyncio
import websockets
import json
import time
import statistics

WS_URL = "ws://localhost:8080/ws/chat"
CONCURRENT_USERS = 10
REQUESTS_PER_USER = 3

async def test_user(user_id, num_requests):
    """测试单个用户的WebSocket连接"""
    results = []
    
    try:
        async with websockets.connect(WS_URL) as ws:
            session_id = f"ws_session_{user_id}_{int(time.time())}"
            
            for req_num in range(1, num_requests + 1):
                message = f"你好，WebSocket并发测试，用户{user_id} 请求{req_num}"
                
                payload = {
                    "userId": f"testuser_{user_id}",
                    "message": message,
                    "sessionId": session_id,
                    "multiTurn": True
                }
                
                start = time.time()
                await ws.send(json.dumps(payload))
                response = await asyncio.wait_for(ws.recv(), timeout=30)
                end = time.time()
                
                duration_ms = (end - start) * 1000
                
                if response:
                    print(f"✅ 用户{user_id} 请求{req_num} 成功 ({duration_ms:.0f}ms)")
                    results.append({
                        "success": True,
                        "duration": duration_ms
                    })
                else:
                    print(f"❌ 用户{user_id} 请求{req_num} 失败")
                    results.append({
                        "success": False,
                        "duration": duration_ms
                    })
                
                await asyncio.sleep(0.5)  # 稍微延迟
                
    except Exception as e:
        print(f"❌ 用户{user_id} 连接失败: {e}")
        results.append({"success": False, "duration": 0})
    
    return results

async def run_test():
    """运行并发测试"""
    print("开始WebSocket并发测试...")
    print("-" * 50)
    
    start_time = time.time()
    
    # 创建并发任务
    tasks = [
        test_user(user_id, REQUESTS_PER_USER)
        for user_id in range(1, CONCURRENT_USERS + 1)
    ]
    
    all_results = await asyncio.gather(*tasks)
    
    end_time = time.time()
    total_time = (end_time - start_time) * 1000
    
    # 统计结果
    total_requests = 0
    success_count = 0
    all_durations = []
    
    for user_results in all_results:
        for result in user_results:
            total_requests += 1
            if result["success"]:
                success_count += 1
                all_durations.append(result["duration"])
    
    fail_count = total_requests - success_count
    success_rate = (success_count / total_requests * 100) if total_requests > 0 else 0
    
    avg_duration = statistics.mean(all_durations) if all_durations else 0
    p50_duration = statistics.median(all_durations) if all_durations else 0
    p95_duration = sorted(all_durations)[int(len(all_durations) * 0.95)] if len(all_durations) > 1 else 0
    
    print("\n" + "=" * 50)
    print("📈 WebSocket测试结果汇总")
    print("=" * 50)
    print(f"总请求数:     {total_requests}")
    print(f"成功请求:     {success_count} ({success_rate:.1f}%)")
    print(f"失败请求:     {fail_count} ({100 - success_rate:.1f}%)")
    print(f"总耗时:       {total_time:.0f}ms")
    print(f"平均响应时间: {avg_duration:.0f}ms")
    print(f"P50响应时间:  {p50_duration:.0f}ms")
    print(f"P95响应时间:  {p95_duration:.0f}ms")
    print(f"吞吐量:       {total_requests / (total_time / 1000):.1f} 请求/秒")
    print("=" * 50)

if __name__ == "__main__":
    asyncio.run(run_test())
PYTHON_SCRIPT
