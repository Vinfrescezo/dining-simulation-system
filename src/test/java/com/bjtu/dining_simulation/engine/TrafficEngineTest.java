package com.bjtu.dining_simulation.engine;
import org.junit.jupiter.api.Test;
import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.model.Student;
import com.bjtu.dining_simulation.model.Window;
import com.bjtu.dining_simulation.service.ResourceManager;
import com.bjtu.dining_simulation.service.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.LinkedList;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // 使用 Mockito 扩展
public class TrafficEngineTest {

    @InjectMocks
    private TrafficEngine trafficEngine; // 被测试的主体

    @Mock private SimulationConfig config;
    @Mock private ResourceManager resourceManager;
    @Mock private SimulationService ctx;

    private Window mockWindow;

    @BeforeEach
    void setUp() {
        // 初始化一个虚拟窗口
        mockWindow = new Window();
        mockWindow.setId("W1");
        mockWindow.setStudentQueue(new LinkedList<>());
    }

    @Test
    void testProcessSpawning_QueueFull_ShouldTriggerLostLogic() {
        // --- 1. Arrange (准备数据与打桩) ---
        // 假设目标总人数100，当前已生成0
        when(ctx.getTargetStudentCount()).thenReturn(10000);
        when(ctx.getGeneratedCount()).thenReturn(0);
        when(ctx.getSimDurationTick()).thenReturn(3600);
        when(ctx.getGlobalTickCounter()).thenReturn(50); // 模拟高峰期 Tick

        // 核心设定：设置排队上限为 5
        when(config.getMaxQueueLength()).thenReturn(5);
        
        // 核心设定：模拟这个窗口已经被 5 个学生占满了
        for (int i = 0; i < 5; i++) {
            mockWindow.getStudentQueue().add(new Student());
        }
        when(resourceManager.getShortestQueueWindow()).thenReturn(mockWindow);

        // --- 2. Act (执行方法) ---
        trafficEngine.processSpawning(ctx);

        // --- 3. Assert (断言验证结果) ---
        // 验证1：是否尝试增加了“已生成人数”？(至少被调用了一次)
        verify(ctx, atLeastOnce()).addGeneratedCount();
        
        // 验证2：由于队列满了，是否触发了“流失人数”增加？(至少被调用了一次)
        verify(ctx, atLeastOnce()).addLostCount();
        
        // 验证3：最关键的，队列长度依然是5，没有新学生被加进去
        assertEquals(5, mockWindow.getStudentQueue().size(), "由于队列已满，不应有新学生加入队列");
    }
}