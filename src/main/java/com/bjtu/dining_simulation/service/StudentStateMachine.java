package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.logic.MovementEngine;
import com.bjtu.dining_simulation.model.Seat;
import com.bjtu.dining_simulation.model.Student;
import com.bjtu.dining_simulation.model.Window;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class StudentStateMachine {

    @Autowired private SimulationConfig config;
    @Autowired private MovementEngine moveEngine;
    @Autowired private ResourceManager resourceManager;
    @Autowired private WaitlistEngine waitlistEngine;

    public void updateStudentStatus(SimulationService ctx) {
        // 1. updateMovingStudents (更新移动中的学生)
        for (Student s : ctx.getStudents()) {
            if (List.of("PATHFINDING", "SEEK_SEAT", "LEAVING").contains(s.getStatus())) {
                boolean arrived = moveEngine.moveTowards(s, s.getTargetX(), s.getTargetY(), config.getMoveSpeed());
                if (arrived) {
                    if ("PATHFINDING".equals(s.getStatus())) s.setStatus("QUEUEING");
                    else if ("SEEK_SEAT".equals(s.getStatus())){
                        s.setStatus("EATING");
                        int eatingTime = moveEngine.calculateNormalTime(config.getEatingMu(), config.getEatingSigma(), 90);
                        s.setRemainingTime(eatingTime);
                    } 
                    else if ("LEAVING".equals(s.getStatus())) {
                        ctx.getStudents().remove(s);
                        ctx.addFinishedCount();
                    }
                }
            }
        }

        // 2. refreshQueueTargets (刷新排队目标的绝对坐标)
        for (Window win : resourceManager.getWindows()) {
            List<Student> queue = (List<Student>) win.getStudentQueue();
            for (int i = 0; i < queue.size(); i++) {
                Student s = queue.get(i);
                if ("ORDERING".equals(s.getStatus())) continue;
                
                double[] spot = resourceManager.getQueueSpot(win, i);
                s.setTargetX(spot[0]);
                s.setTargetY(spot[1]);
                if ("QUEUEING".equals(s.getStatus())) {
                    moveEngine.moveTowards(s, spot[0], spot[1], config.getMoveSpeed());
                }
            }
        }

        // 3. processWindows (处理窗口点餐逻辑)
        for (Window win : resourceManager.getWindows()) {
            // A. 如果当前有人正在点餐
            if (win.getOrderingStudentId() != null) {
                Student current = findStudentById(ctx, win.getOrderingStudentId());
                if (current != null && "ORDERING".equals(current.getStatus())) {
                    current.setRemainingTime(current.getRemainingTime() - 1);
                    if (current.getRemainingTime() <= 0) {
                        finishOrdering(ctx, win, current);
                    }
                }
            }
            
            // B. 如果窗口空闲，且队列里有人
            if (win.getOrderingStudentId() == null && !win.getStudentQueue().isEmpty()) {
                Student next = win.getStudentQueue().peek();
                if (next != null && "QUEUEING".equals(next.getStatus())) {
                    win.setOrderingStudentId(next.getId());
                    next.setStatus("ORDERING");
                    next.setTargetX(win.getX());
                    next.setTargetY(win.getY() + 24);
                    next.setX(win.getX());
                    next.setY(win.getY() + 24);
                    
                    next.setRemainingTime(moveEngine.calculateNormalTime(config.getOrderingMu(), config.getOrderingSigma(), 5));
                    
                    next.setTotalWaitTime(ctx.getGlobalTickCounter() - next.getQueueStartTick());
                    ctx.addTotalQueueTime(next.getTotalWaitTime());
                }
            }
        }

        // 4. updateEatingStudents (处理吃饭与离场逻辑)
        for (Student s : ctx.getStudents()) {
            if ("EATING".equals(s.getStatus())) {
                s.setRemainingTime(s.getRemainingTime() - 1);
                if (s.getRemainingTime() <= 0) {
                    resourceManager.releaseSeat(s.getId());
                    ctx.addTotalEatingTime(360);
                    s.setStatus("LEAVING");
                    s.setTargetX(config.getEXIT_X());
                    s.setTargetY(config.getEXIT_Y());
                }
            }
        }
    }

    // 完美复刻前端的 finishOrdering
    private void finishOrdering(SimulationService ctx, Window win, Student student) {
        win.getStudentQueue().remove(student);
        win.setOrderingStudentId(null);
        win.setServed(win.getServed() + 1);

        if (resourceManager.hasEmptySeat()) {
            resourceManager.tryToOccupySeat(student);
            Seat seat = resourceManager.findSeatByStudentId(student.getId());
            student.setTargetX(seat.getX());
            student.setTargetY(seat.getY());
            student.setStatus("SEEK_SEAT");
        } else {
            student.setStatus("WAITING_FOR_SEAT");
            student.setSeatWaitStartTick(ctx.getGlobalTickCounter());
            waitlistEngine.joinWaitlist(student.getId());
        }
    }

    private Student findStudentById(SimulationService ctx, String id) {
        return ctx.getStudents().stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }
}