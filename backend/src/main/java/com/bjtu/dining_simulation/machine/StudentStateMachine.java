package com.bjtu.dining_simulation.machine;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.engine.MovementEngine;
import com.bjtu.dining_simulation.engine.WaitlistEngine;
import com.bjtu.dining_simulation.model.Seat;
import com.bjtu.dining_simulation.model.Student;
import com.bjtu.dining_simulation.model.Window;
import com.bjtu.dining_simulation.service.ResourceManager;
import com.bjtu.dining_simulation.service.SimulationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class StudentStateMachine {

    @Autowired private SimulationConfig config;
    @Autowired private MovementEngine moveEngine;
    @Autowired private ResourceManager resourceManager;
    @Autowired private WaitlistEngine waitlistEngine;

    public void updateStudentStatus(SimulationService ctx) {
        Set<String> livingIds = ctx.getStudents().stream().map(Student::getId).collect(Collectors.toSet());

        for (Student s : ctx.getStudents()) {
            if ("PATHFINDING".equals(s.getStatus()) || "LEAVING".equals(s.getStatus())) {
                boolean arrived = moveEngine.moveTowards(s, s.getTargetX(), s.getTargetY(), config.getMoveSpeed());
                if (arrived) {
                    if ("PATHFINDING".equals(s.getStatus())) {
                        s.setStatus("QUEUEING");
                    } else {
                        ctx.getStudents().remove(s);
                        ctx.addFinishedCount();
                    }
                }
            } else if ("SEEK_SEAT".equals(s.getStatus())) {
                handleSeekSeat(ctx, s);
            }
        }

        for (Window win : resourceManager.getWindows()) {
            // purge ghost entries
            win.getStudentQueue().removeIf(s -> !livingIds.contains(s.getId()));

            // update queue positions (skip ORDERING student)
            int visualIndex = 0;
            for (Student s : win.getStudentQueue()) {
                if ("ORDERING".equals(s.getStatus())) continue;
                double[] spot = resourceManager.getQueueSpot(win, visualIndex);
                s.setTargetX(spot[0]);
                s.setTargetY(spot[1]);
                if ("QUEUEING".equals(s.getStatus())) {
                    moveEngine.moveTowards(s, spot[0], spot[1], config.getMoveSpeed());
                }
                visualIndex++;
            }
        }

        for (Window win : resourceManager.getWindows()) {
            // check current ordering student
            if (win.getOrderingStudentId() != null) {
                Student current = findStudentById(ctx, win.getOrderingStudentId());
                if (current != null && "ORDERING".equals(current.getStatus())) {
                    current.setRemainingTime(current.getRemainingTime() - 1);
                    if (current.getRemainingTime() <= 0) {
                        finishOrdering(ctx, win, current);
                    }
                } else {
                    // student gone or wrong state — unstick
                    if (current != null) {
                        win.getStudentQueue().remove(current);
                    }
                    win.setOrderingStudentId(null);
                }
            }

            // find first QUEUEING student to serve (skip PATHFINDING at head)
            if (win.getOrderingStudentId() == null && !win.getStudentQueue().isEmpty()) {
                Student next = null;
                for (Student s : win.getStudentQueue()) {
                    if ("QUEUEING".equals(s.getStatus())) {
                        next = s;
                        break;
                    }
                }
                if (next != null) {
                    win.setOrderingStudentId(next.getId());
                    next.setStatus("ORDERING");
                    next.setTargetX(win.getX());
                    next.setTargetY(win.getY() + 28);
                    next.setX(win.getX());
                    next.setY(win.getY() + 28);

                    int orderingTime = moveEngine.calculateNormalTime(
                            config.getOrderingMu() + win.getServiceWeight() * 2.0,
                            config.getOrderingSigma(),
                            5);
                    next.setOrderingDuration(orderingTime);
                    next.setRemainingTime(orderingTime);
                    ctx.addTotalOrderingTime(orderingTime);

                    next.setOrderStartTick(ctx.getGlobalTickCounter());
                    int queueTime = Math.max(0, next.getOrderStartTick() - next.getQueueStartTick());
                    next.setQueueTime(queueTime);
                    next.setTotalWaitTime(queueTime);
                    ctx.addTotalQueueTime(queueTime);
                    ctx.addOrderingStartedCount();
                    win.recordQueueTime(queueTime);
                }
            }
        }

        for (Student s : ctx.getStudents()) {
            if ("EATING".equals(s.getStatus())) {
                s.setRemainingTime(s.getRemainingTime() - 1);
                if (s.getRemainingTime() <= 0) {
                    resourceManager.releaseSeat(s.getId());
                    ctx.addTotalEatingTime(s.getEatingDuration());
                    s.setStatus("LEAVING");
                    s.setTargetX(config.getEXIT_X());
                    s.setTargetY(config.getEXIT_Y());
                }
            }
        }
    }

    private void handleSeekSeat(SimulationService ctx, Student student) {
        Seat reservedSeat = resourceManager.findReservedSeatByStudentId(student.getId());
        if (reservedSeat != null) {
            student.setTargetX(reservedSeat.getX());
            student.setTargetY(reservedSeat.getY());
            boolean arrived = moveEngine.moveTowards(student, reservedSeat.getX(), reservedSeat.getY(), config.getMoveSpeed() * 0.7);
            if (arrived) {
                resourceManager.markReservedSeatOccupied(student);
                student.setStatus("EATING");
                int eatingTime = moveEngine.calculateNormalTime(config.getEatingMu(), config.getEatingSigma(), 90);
                student.setEatingDuration(eatingTime);
                student.setRemainingTime(eatingTime);
            }
            return;
        }

        Seat newlyReserved = resourceManager.reserveSeatForStudent(student);
        if (newlyReserved != null) {
            student.setTargetX(newlyReserved.getX());
            student.setTargetY(newlyReserved.getY());
            return;
        }

        if (student.getSeatSearchStartTick() < 0) {
            startSeatSearching(student, ctx.getGlobalTickCounter());
        }
        if (ctx.getGlobalTickCounter() - student.getSeatSearchStartTick() >= student.getSeatSearchDurationTick()) {
            moveToWaitlistOrLeave(ctx, student);
            return;
        }

        double[] pos = resourceManager.getSeatSearchRoamingSpot(student, ctx.getGlobalTickCounter());
        student.setTargetX(pos[0]);
        student.setTargetY(pos[1]);
        moveEngine.moveTowards(student, pos[0], pos[1], Math.max(3.0, config.getMoveSpeed() * 0.32));
    }

    private void finishOrdering(SimulationService ctx, Window win, Student student) {
        win.getStudentQueue().remove(student);
        win.setOrderingStudentId(null);
        win.setServed(win.getServed() + 1);

        Seat reserved = resourceManager.reserveSeatForStudent(student);
        if (reserved != null) {
            student.setTargetX(reserved.getX());
            student.setTargetY(reserved.getY());
            student.setStatus("SEEK_SEAT");
            student.setSeatSearchStartTick(-1);
            return;
        }

        startSeatSearching(student, ctx.getGlobalTickCounter());
        double[] pos = resourceManager.getSeatSearchRoamingSpot(student, ctx.getGlobalTickCounter());
        student.setTargetX(pos[0]);
        student.setTargetY(pos[1]);
        student.setStatus("SEEK_SEAT");
    }

    private void startSeatSearching(Student student, int tick) {
        student.setTargetId("SEARCHING_SEAT");
        student.setReservedSeatId(null);
        student.setSeatSearchStartTick(tick);
        student.setSeatSearchDurationTick(ThreadLocalRandom.current().nextInt(12, 31));
    }

    private void moveToWaitlistOrLeave(SimulationService ctx, Student student) {
        if (waitlistEngine.canJoinWaitlist()) {
            student.setStatus("WAITING_FOR_SEAT");
            student.setSeatWaitStartTick(ctx.getGlobalTickCounter());
            student.setSeatSearchStartTick(-1);
            waitlistEngine.joinWaitlist(student.getId());
        } else {
            student.setStatus("LEAVING");
            student.setTargetX(config.getEXIT_X());
            student.setTargetY(config.getEXIT_Y());
            student.setSeatSearchStartTick(-1);
            ctx.addSeatAbandonCount();
        }
    }

    private Student findStudentById(SimulationService ctx, String id) {
        return ctx.getStudents().stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }
}
