package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.engine.TrafficEngine;
import com.bjtu.dining_simulation.engine.WaitlistEngine;
import com.bjtu.dining_simulation.machine.StudentStateMachine;
import com.bjtu.dining_simulation.model.*;
import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.dto.StartConfigDTO;
import com.bjtu.dining_simulation.dto.StartResponseDTO;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Getter 
@Setter
public class SimulationService {

    @Autowired private ResourceManager resourceManager;
    @Autowired private TrafficEngine trafficEngine;
    @Autowired private WaitlistEngine waitlistEngine;
    @Autowired private StudentStateMachine stateMachine;
    @Autowired private SimulationConfig simulationConfig;
    // --- 全局基础数据 ---
    private int globalTickCounter = 0;
    private int targetStudentCount = 1500;
    private int simDurationTick = 3600;

    // --- 统计数据 ---
    private int generatedCount = 0;
    private int lostCount = 0;      
    private int finishedCount = 0;  
    private int totalQueueTime = 0;
    private int totalSeatWaitTime = 0;
    private int totalEatingTime = 0;
    private int maxCongestion = 0;

    // --- 场内实体 ---
    private final List<Student> students = new CopyOnWriteArrayList<>();

    // 桥接方法
    public List<Window> getWindows() { return resourceManager.getWindows(); }
    public List<Seat> getSeats() { return resourceManager.getSeats(); }

    public StartResponseDTO startSimulation(StartConfigDTO config) {
        // 1. 处理兜底默认值（防止前端传空数据）
        int seats = config.getSeatCount() > 0 ? config.getSeatCount() : 240;
        int windows = config.getWindowCount() > 0 ? config.getWindowCount() : 10;
        int duration = config.getSimDurationTick() > 0 ? config.getSimDurationTick() : 3600;
        int maxQueue = config.getMaxQueueLength() > 0 ? config.getMaxQueueLength() : 20;
        simulationConfig.setMaxQueueLength(maxQueue);
        System.out.println("### 成功更新全局配置！maxQueueLength 现在是: " + simulationConfig.getMaxQueueLength());
        // 2. 调用你原本写好的底层重置方法
        this.resetSimulation(config.getStudentCount(), windows, duration, seats);

        // 3. 组装返回给前端的成功响应
        StartResponseDTO response = new StartResponseDTO();
        response.setStatus("success");
        response.setMessage("仿真已根据新参数重新启动");
        response.setSimId("SERVER_" + System.currentTimeMillis()); 
        
        return response;
    }

    /**
     * 底层重置与初始化 (保持不变)
     */
    public void resetSimulation(int studentCount, int windowCount, int durationTick, int seatCount) {
        this.globalTickCounter = 0;
        this.generatedCount = 0;
        this.lostCount = 0;
        this.finishedCount = 0;
        this.totalQueueTime = 0;
        this.totalSeatWaitTime = 0;
        this.totalEatingTime = 0;
        this.maxCongestion = 0;
        this.targetStudentCount = studentCount;
        this.simDurationTick = durationTick;
        this.students.clear();
        
        resourceManager.initResources(windowCount, seatCount);
        trafficEngine.reset();
        waitlistEngine.reset();
    }

    /**
     * 核心生命周期节拍器 (保持不变)
     */
    @Scheduled(fixedRate = 100)
    public void runTick() {
        if (globalTickCounter >= simDurationTick || 
           (generatedCount >= targetStudentCount && students.isEmpty())) {
            return; // 仿真已结束
        }

        globalTickCounter++;
        maxCongestion = Math.max(maxCongestion, students.size());

        // 1. 流量引擎生成学生
        trafficEngine.processSpawning(this);
        // 2. 排号引擎分配座位
        waitlistEngine.allocateSeatsToWaitingStudents(this);
        // 3. 状态机引擎推演行为
        stateMachine.updateStudentStatus(this);
        
        if(globalTickCounter % 10 == 0) {
            System.out.println("--- Tick: " + globalTickCounter + " | 场内人数: " + students.size() + " ---");
        }
    }
    
    // --- 提供给子引擎的累加方法 (保持不变) ---
    public void addGeneratedCount() { this.generatedCount++; }
    public void addLostCount() { this.lostCount++; }
    public void addFinishedCount() { this.finishedCount++; }
    public void addTotalQueueTime(int time) { this.totalQueueTime += time; }
    public void addTotalSeatWaitTime(int time) { this.totalSeatWaitTime += time; }
    public void addTotalEatingTime(int time) { this.totalEatingTime += time; }
}