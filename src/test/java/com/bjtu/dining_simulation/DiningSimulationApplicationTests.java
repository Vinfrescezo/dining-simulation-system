package com.bjtu.dining_simulation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertEquals;

//@SpringBootTest
class DiningSimulationApplicationTests {

	@Test
	void contextLoads() {
	}
	@Test
    void testQueueCalculation() {
        // 1. 准备数据 (模拟排队情况)
        int currentQueue = 5; // 假设档口目前有5个人排队
        int newStudents = 2;  // 突然又来了2个学生

        // 2. 执行你的业务逻辑 (这里用简单的加法代替你的复杂算法)
        int result = currentQueue + newStudents;

        // 3. 断言 (Assert) —— JUnit的灵魂！
        // 意思是：我预期结果是7，实际算出来是result。如果不等于7，就报错并提示后面的文字。
        assertEquals(7, result);
    }
}
