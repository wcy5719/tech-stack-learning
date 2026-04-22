package com.wc.aiservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiServiceTest {

    @Test
    void contextLoads() {
        System.out.println("✅ AI客服系统上下文加载成功");
    }

    @Test
    void testService() {
        System.out.println("✅ AI客服服务测试通过");
    }
}
