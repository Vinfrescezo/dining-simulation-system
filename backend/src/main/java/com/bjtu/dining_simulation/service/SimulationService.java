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

import java.util.*;
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

    private int globalTickCounter = 0;
    private int targetStudentCount = 1500;
    private int simDurationTick = 3600;

    private int generatedCount = 0;
    private int lostCount = 0;
    private int queueLostCount = 0;
    private int seatAbandonCount = 0;
    private int finishedCount = 0;
    private int orderingStartedCount = 0;
    private int seatWaitCompletedCount = 0;
    private int totalQueueTime = 0;
    private int totalSeatWaitTime = 0;
    private int totalEatingTime = 0;
    private int totalOrderingTime = 0;
    private int maxCongestion = 0;
    private int maxSeatWaiting = 0;

    private boolean paused = false;
    private double speedFactor = 1.0;
    private double slowSpeedCarry = 0.0;
    private boolean finishReported = false;
    private boolean reportSaved = false;
    private String currentSimId = "SERVER_INIT";

    private final List<Map<String, Object>> trendHistory = new CopyOnWriteArrayList<>();
    private final Map<String, List<Integer>> queueLengthSamples = new HashMap<>();
    private final List<Student> students = new CopyOnWriteArrayList<>();

    public List<Window> getWindows() { return resourceManager.getWindows(); }
    public List<Seat> getSeats() { return resourceManager.getSeats(); }

    public StartResponseDTO startSimulation(StartConfigDTO config) {
        int seats = config.getSeatCount() > 0 ? config.getSeatCount() : 240;
        int windows = config.getWindowCount() > 0 ? config.getWindowCount() : 10;
        int duration = config.getSimDurationTick() > 0 ? config.getSimDurationTick() : 3600;
        int maxQueue = config.getMaxQueueLength() > 0 ? config.getMaxQueueLength() : 30;
        int maxSeatWait = config.getMaxSeatWaitCapacity() > 0 ? config.getMaxSeatWaitCapacity() : Math.max(60, seats / 2);
        int maxSeatWaitTick = config.getMaxSeatWaitTick() > 0 ? config.getMaxSeatWaitTick() : 240;
        simulationConfig.setMaxQueueLength(maxQueue);
        simulationConfig.setMaxSeatWaitCapacity(maxSeatWait);
        simulationConfig.setMaxSeatWaitTick(maxSeatWaitTick);

        this.resetSimulation(config.getStudentCount(), windows, duration, seats);

        StartResponseDTO response = new StartResponseDTO();
        response.setCode(200);
        response.setStatus("success");
        response.setMsg("仿真引擎初始化成功");
        response.setMessage("仿真已根据新参数重新启动");
        response.setSimId(currentSimId);
        response.setData(Map.of("simId", currentSimId));
        return response;
    }

    public void resetSimulation(int studentCount, int windowCount, int durationTick, int seatCount) {
        this.globalTickCounter = 0;
        this.generatedCount = 0;
        this.lostCount = 0;
        this.queueLostCount = 0;
        this.seatAbandonCount = 0;
        this.finishedCount = 0;
        this.orderingStartedCount = 0;
        this.seatWaitCompletedCount = 0;
        this.totalQueueTime = 0;
        this.totalSeatWaitTime = 0;
        this.totalEatingTime = 0;
        this.totalOrderingTime = 0;
        this.maxCongestion = 0;
        this.maxSeatWaiting = 0;
        this.targetStudentCount = studentCount;
        this.simDurationTick = durationTick;
        this.paused = false;
        this.speedFactor = 1.0;
        this.slowSpeedCarry = 0.0;
        this.finishReported = false;
        this.reportSaved = false;
        this.currentSimId = "SERVER_" + System.currentTimeMillis();
        this.students.clear();
        this.trendHistory.clear();
        this.queueLengthSamples.clear();

        resourceManager.initResources(windowCount, seatCount);
        trafficEngine.reset();
        waitlistEngine.reset();
    }

    @Scheduled(fixedRate = 100)
    public void runTick() {
        if (paused || isFinished()) return;
        int steps = computeStepsThisFrame();
        for (int i = 0; i < steps && !isFinished(); i++) {
            runOneTick();
        }
    }

    private int computeStepsThisFrame() {
        if (speedFactor >= 1.0) return Math.max(1, Math.min(8, (int)Math.round(speedFactor)));
        slowSpeedCarry += speedFactor;
        if (slowSpeedCarry >= 1.0) {
            slowSpeedCarry -= 1.0;
            return 1;
        }
        return 0;
    }

    private void runOneTick() {
        globalTickCounter++;
        maxCongestion = Math.max(maxCongestion, students.size());
        maxSeatWaiting = Math.max(maxSeatWaiting, waitlistEngine.getWaitingSeatQueue().size());

        for (com.bjtu.dining_simulation.model.Seat seat : resourceManager.getSeats()) {
            if (seat.isOccupied()) seat.setOccupancyTicks(seat.getOccupancyTicks() + 1);
        }

        trafficEngine.processSpawning(this);
        waitlistEngine.allocateSeatsToWaitingStudents(this);
        waitlistEngine.removeOvertimeWaitingStudents(this);
        stateMachine.updateStudentStatus(this);
        sampleStats();

        if(globalTickCounter % 50 == 0) {
            System.out.println("--- Tick: " + globalTickCounter + " | 场内人数: " + students.size() + " | 等座人数: " + waitlistEngine.getWaitingSeatQueue().size() + " ---");
        }
    }

    public boolean isFinished() {
        return globalTickCounter >= simDurationTick || (generatedCount >= targetStudentCount && students.isEmpty());
    }

    public void setSpeedFactor(double factor) {
        this.speedFactor = Math.max(0.5, Math.min(8.0, factor));
    }

    public boolean markFinishReported() {
        if (finishReported) return false;
        finishReported = true;
        return true;
    }

    public boolean markReportSaved() {
        if (reportSaved) return false;
        reportSaved = true;
        return true;
    }

    private void sampleStats() {
        if (globalTickCounter % 10 != 0 && globalTickCounter != 1) return;
        Map<String, Object> point = new HashMap<>();
        point.put("tick", globalTickCounter);
        point.put("activeCount", students.size());
        point.put("occupiedSeats", resourceManager.getSeats().stream().filter(Seat::isOccupied).count());
        point.put("waitingSeatCount", waitlistEngine.getWaitingSeatQueue().size());
        point.put("avgWaitTime", getRealtimeAvgQueueTime());
        point.put("avgSeatWaitTime", getRealtimeAvgSeatWaitTime());
        trendHistory.add(point);

        for (Window w : resourceManager.getWindows()) {
            w.updatePeakQueueLength();
            queueLengthSamples.computeIfAbsent(w.getId(), k -> new ArrayList<>()).add(w.getWaitingQueueLength());
        }
    }

    public double getRealtimeAvgQueueTime() {
        long currentWaitingTime = 0L;
        int currentWaitingCount = 0;
        for (Window w : resourceManager.getWindows()) {
            currentWaitingTime += w.getCurrentWaitingTimeSum(globalTickCounter);
            currentWaitingCount += w.getCurrentWaitingStudentCount();
        }
        long total = totalQueueTime + currentWaitingTime;
        int count = orderingStartedCount + currentWaitingCount;
        return count > 0 ? (double) total / count : 0.0;
    }

    public double getRealtimeAvgSeatWaitTime() {
        return seatWaitCompletedCount > 0 ? (double) totalSeatWaitTime / seatWaitCompletedCount : 0.0;
    }

    public double getRealtimeAvgEatingTime() {
        return finishedCount > 0 ? (double) totalEatingTime / finishedCount : 0.0;
    }

    public double getLossRate() {
        return generatedCount > 0 ? (double) lostCount / generatedCount : 0.0;
    }

    public void addGeneratedCount() { this.generatedCount++; }
    public void addLostCount() { this.lostCount++; }
    public void addQueueLostCount() { this.queueLostCount++; this.lostCount++; }
    public void addSeatAbandonCount() { this.seatAbandonCount++; this.lostCount++; }
    public void addFinishedCount() { this.finishedCount++; }
    public void addOrderingStartedCount() { this.orderingStartedCount++; }
    public void addSeatWaitCompletedCount() { this.seatWaitCompletedCount++; }
    public void addTotalQueueTime(int time) { this.totalQueueTime += time; }
    public void addTotalSeatWaitTime(int time) { this.totalSeatWaitTime += time; }
    public void addTotalEatingTime(int time) { this.totalEatingTime += time; }
    public void addTotalOrderingTime(int time) { this.totalOrderingTime += time; }
}
