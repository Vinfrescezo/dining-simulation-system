package com.bjtu.dining_simulation.engine;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.model.Seat;
import com.bjtu.dining_simulation.model.Student;
import com.bjtu.dining_simulation.service.ResourceManager;
import com.bjtu.dining_simulation.service.SimulationService;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WaitlistEngine {

    @Autowired 
    private ResourceManager resourceManager;
    
    @Autowired 
    private MovementEngine moveEngine;
    
    @Autowired 
    private SimulationConfig config;
    
    @Getter
    private final List<String> waitingSeatQueue = new ArrayList<>();

    public void reset() {
        this.waitingSeatQueue.clear();
    }

    public void joinWaitlist(String studentId) {
        if (!this.waitingSeatQueue.contains(studentId)) {
            this.waitingSeatQueue.add(studentId);
        }
    }

    public void allocateSeatsToWaitingStudents(SimulationService ctx) {
        if (waitingSeatQueue.isEmpty()) return;
        
        // 1. 尝试给等候队列头部的学生分配座位
        while (!waitingSeatQueue.isEmpty() && resourceManager.hasEmptySeat()) {
            String studentId = waitingSeatQueue.remove(0);
            for (Student s : ctx.getStudents()) {
                if (s.getId().equals(studentId) && "WAITING_FOR_SEAT".equals(s.getStatus())) {
                    resourceManager.tryToOccupySeat(s);
                    Seat seat = resourceManager.findSeatByStudentId(s.getId());
                    if (seat != null) {
                        s.setTargetX(seat.getX());
                        s.setTargetY(seat.getY());
                        s.setStatus("SEEK_SEAT");
                        
                        // 统计等座耗时
                        if (s.getSeatWaitStartTick() != null) {
                            s.setTotalSeatWaitTime(ctx.getGlobalTickCounter() - s.getSeatWaitStartTick());
                            ctx.addTotalSeatWaitTime(s.getTotalSeatWaitTime());
                            s.setSeatWaitStartTick(null);
                        }
                    }
                    break;
                }
            }
        }

        // 2. 更新剩下还在等座的学生的物理坐标 (实现排队效果)
        for (int i = 0; i < waitingSeatQueue.size(); i++) {
            String sid = waitingSeatQueue.get(i);
            double[] pos = resourceManager.getWaitingSpot(i);
            
            ctx.getStudents().stream()
               .filter(s -> s.getId().equals(sid))
               .findFirst()
               .ifPresent(s -> {
                   s.setTargetX(pos[0]);
                   s.setTargetY(pos[1]);
                   // 补全这里缺失的物理移动调用
                   moveEngine.moveTowards(s, pos[0], pos[1], config.getMoveSpeed());
               });
        }
    }
}