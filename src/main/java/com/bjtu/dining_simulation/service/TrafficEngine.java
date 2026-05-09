package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.model.Student;
import com.bjtu.dining_simulation.model.Window;
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
        int remaining = ctx.getTargetStudentCount() - ctx.getGeneratedCount();
        if (remaining <= 0) return;

        double t = (double) ctx.getGlobalTickCounter() / Math.max(1, ctx.getSimDurationTick());
        
        // 双峰指数方程
        double rushCurve = 0.42 
            + 1.7 * Math.exp(-Math.pow((t - 0.22) / 0.18, 2)) 
            + 0.42 * Math.exp(-Math.pow((t - 0.55) / 0.24, 2));
            
        double baseRate = (double) ctx.getTargetStudentCount() / Math.max(1, ctx.getSimDurationTick());
        spawnAccumulator += baseRate * rushCurve;

        int countToSpawn = Math.min(remaining, (int) Math.floor(spawnAccumulator));
        spawnAccumulator -= countToSpawn;

        for (int i = 0; i < countToSpawn; i++) {
            Window target = resourceManager.getRandomWindow();
            if (resourceManager.isAllWindowFull()) {
                ctx.addGeneratedCount();
                ctx.addLostCount(); // 队列全满，流失
                continue;
            }

            String sId = "学生-" + UUID.randomUUID().toString().substring(0, 4);
            Student s = new Student(sId, config.DOOR_X, config.DOOR_Y, "PATHFINDING", target.getId(), 0);
            s.setTargetX(target.getX());
            s.setTargetY(target.getY() + 50); 
            
            ctx.getStudents().add(s);
            target.getStudentQueue().add(s);
            ctx.addGeneratedCount();
        }
    }
}