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
import java.util.Iterator;
import java.util.List;

@Component
public class WaitlistEngine {

    @Autowired private ResourceManager resourceManager;
    @Autowired private MovementEngine moveEngine;
    @Autowired private SimulationConfig config;

    @Getter
    private final List<String> waitingSeatQueue = new ArrayList<>();

    public void reset() {
        this.waitingSeatQueue.clear();
    }

    public boolean canJoinWaitlist() {
        return waitingSeatQueue.size() < config.getMaxSeatWaitCapacity();
    }

    public void joinWaitlist(String studentId) {
        if (!this.waitingSeatQueue.contains(studentId)) {
            this.waitingSeatQueue.add(studentId);
        }
    }

    public void allocateSeatsToWaitingStudents(SimulationService ctx) {
        if (waitingSeatQueue.isEmpty()) return;

        while (!waitingSeatQueue.isEmpty() && resourceManager.hasEmptySeat()) {
            String studentId = waitingSeatQueue.remove(0);
            for (Student s : ctx.getStudents()) {
                if (s.getId().equals(studentId) && "WAITING_FOR_SEAT".equals(s.getStatus())) {
                    Seat seat = resourceManager.reserveSeatForStudent(s);
                    if (seat != null) {
                        s.setTargetX(seat.getX());
                        s.setTargetY(seat.getY());
                        s.setStatus("SEEK_SEAT");
                        s.setSeatSearchStartTick(-1);
                        if (s.getSeatWaitStartTick() != null) {
                            s.setTotalSeatWaitTime(ctx.getGlobalTickCounter() - s.getSeatWaitStartTick());
                            ctx.addTotalSeatWaitTime(s.getTotalSeatWaitTime());
                            ctx.addSeatWaitCompletedCount();
                            s.setSeatWaitStartTick(null);
                        }
                    }
                    break;
                }
            }
        }
        updateWaitingPositions(ctx);
    }

    public void removeOvertimeWaitingStudents(SimulationService ctx) {
        if (waitingSeatQueue.isEmpty()) return;
        Iterator<String> iterator = waitingSeatQueue.iterator();
        while (iterator.hasNext()) {
            String sid = iterator.next();
            Student s = ctx.getStudents().stream().filter(item -> item.getId().equals(sid)).findFirst().orElse(null);
            if (s == null || !"WAITING_FOR_SEAT".equals(s.getStatus())) {
                iterator.remove();
                continue;
            }
            if (s.getSeatWaitStartTick() != null && ctx.getGlobalTickCounter() - s.getSeatWaitStartTick() >= config.getMaxSeatWaitTick()) {
                iterator.remove();
                s.setStatus("LEAVING");
                s.setTargetX(config.getEXIT_X());
                s.setTargetY(config.getEXIT_Y());
                s.setSeatWaitStartTick(null);
                ctx.addSeatAbandonCount();
            }
        }
        updateWaitingPositions(ctx);
    }

    private void updateWaitingPositions(SimulationService ctx) {
        for (int i = 0; i < waitingSeatQueue.size(); i++) {
            String sid = waitingSeatQueue.get(i);
            double[] pos = resourceManager.getWaitingRoamingSpot(i, ctx.getGlobalTickCounter());
            ctx.getStudents().stream()
               .filter(s -> s.getId().equals(sid))
               .findFirst()
               .ifPresent(s -> {
                   s.setTargetX(pos[0]);
                   s.setTargetY(pos[1]);
                   moveEngine.moveTowards(s, pos[0], pos[1], Math.max(2.0, config.getMoveSpeed() * 0.22));
               });
        }
    }
}
