package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.model.*;
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

    /**
     * 重置与初始化
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
     * 核心生命周期节拍器：只负责按顺序调用三大引擎
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
    
    // --- 提供给子引擎的累加方法 ---
    public void addGeneratedCount() { this.generatedCount++; }
    public void addLostCount() { this.lostCount++; }
    public void addFinishedCount() { this.finishedCount++; }
    public void addTotalQueueTime(int time) { this.totalQueueTime += time; }
    public void addTotalSeatWaitTime(int time) { this.totalSeatWaitTime += time; }
    public void addTotalEatingTime(int time) { this.totalEatingTime += time; }
}