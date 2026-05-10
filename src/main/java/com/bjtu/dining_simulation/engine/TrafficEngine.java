package com.bjtu.dining_simulation.engine;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.model.Student;
import com.bjtu.dining_simulation.model.Window;
import com.bjtu.dining_simulation.service.ResourceManager;
import com.bjtu.dining_simulation.service.SimulationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TrafficEngine {

    @Autowired private SimulationConfig config;
    @Autowired private ResourceManager resourceManager;

    private double spawnAccumulator = 0.0;

    public void reset() {
        this.spawnAccumulator = 0.0;
    }

    public void processSpawning(SimulationService ctx) {
        // 1. 计算还需生成多少人 (此时的 generatedCount 已包含所有已生成的流失者和进场者)
        int remaining = ctx.getTargetStudentCount() - ctx.getGeneratedCount();
        if (remaining <= 0) return;

        // 2. 双峰指数方程计算当前 Tick 应该生成的速率
        double t = (double) ctx.getGlobalTickCounter() / Math.max(1, ctx.getSimDurationTick());
        double rushCurve = 0.42 
            + 1.7 * Math.exp(-Math.pow((t - 0.22) / 0.18, 2)) 
            + 0.42 * Math.exp(-Math.pow((t - 0.55) / 0.24, 2));
            
        double baseRate = (double) ctx.getTargetStudentCount() / Math.max(1, ctx.getSimDurationTick());
        spawnAccumulator += baseRate * rushCurve;

        // 3. 确定本次 Tick 实际生成的学生数量
        int countToSpawn = Math.min(remaining, (int) Math.floor(spawnAccumulator));
        spawnAccumulator -= countToSpawn;

        // 4. 循环生成逻辑
        for (int i = 0; i < countToSpawn; i++) {
            // 流失的学生也会占用 targetStudentCount 的配额
            ctx.addGeneratedCount(); 

            // 寻找当前排队最短的窗口
            Window targetWindow = resourceManager.getShortestQueueWindow();

            // 检查该窗口是否已经达到排队上限
            if (targetWindow.getStudentQueue().size() >= config.getMaxQueueLength()) {
                // 如果满了，直接记录为流失，不再创建学生对象，直接跳过本次循环
                ctx.addLostCount(); 
                continue; 
            }

            // --- 只有窗口没满，才真正创建学生实体 ---
            String sId = "学生-" + UUID.randomUUID().toString().substring(0, 4);
            Student s = new Student(sId, config.getDOOR_X(), config.getDOOR_Y(), "PATHFINDING", targetWindow.getId(), 0);
            
            // 设置目标点（窗口下方位置）
            s.setTargetX(targetWindow.getX());
            s.setTargetY(targetWindow.getY() + 50); 
            
            // 记录开始排队的时间戳（用于后续在状态机中计算等待超时流失）
            s.setQueueStartTick(ctx.getGlobalTickCounter());
            
            // 将学生加入全局列表（场内实时列表）
            ctx.getStudents().add(s);
            
            // 将学生加入对应的窗口队列
            targetWindow.getStudentQueue().add(s);
        }
    }
}