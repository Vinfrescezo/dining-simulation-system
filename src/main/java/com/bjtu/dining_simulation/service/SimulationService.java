package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.logic.MovementEngine;
import com.bjtu.dining_simulation.model.*;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Getter // 自动生成 getGlobalTickCounter() 和 getStudents()
public class SimulationService {

    @Autowired private SimulationConfig config;
    @Autowired private MovementEngine moveEngine;
    @Autowired private ResourceManager resourceManager;

    private int globalTickCounter = 0; // 变量名与报错中的 Getter 对应
    private final List<Student> students = new CopyOnWriteArrayList<>();

    // 桥接方法：因为 Controller 习惯直接向 Service 要窗口和座位
    public List<Window> getWindows() { return resourceManager.getWindows(); }
    public List<Seat> getSeats() { return resourceManager.getSeats(); }

    @Scheduled(fixedRate = 1000)
    public void runTick() {
        globalTickCounter++;
        if (globalTickCounter % config.getStudentSpawnRate() == 0) spawnStudent();
        updateStudentStatus();
    }

    private void spawnStudent() {
        if (resourceManager.isAllWindowFull()) return;
        Window target = resourceManager.getRandomWindow();
        String sId = "学生-" + UUID.randomUUID().toString().substring(0, 4);
        Student s = new Student(sId, config.DOOR_X, config.DOOR_Y, "PATHFINDING", target.getId(), 0);
        students.add(s);
        target.getStudentQueue().add(s);
    }

    private void updateStudentStatus() {
        for (Student s : students) {
            switch (s.getStatus()) {
                case "PATHFINDING":
                    double winX = s.getTargetId().contains("A") ? 130 : 570;
                    if (moveEngine.moveTowards(s, winX, config.WINDOW_Y, config.getMoveSpeed())) {
                        s.setStatus("QUEUEING");
                    }
                    break;
                case "QUEUEING":
                    Window w = resourceManager.getWindowById(s.getTargetId());
                    if (resourceManager.isFirstInQueue(w, s)) {
                        s.setStatus("ORDERING");
                        s.setRemainingTime(moveEngine.calculateNormalTime(config.getOrderingMu(), config.getOrderingSigma(), 5));
                    }
                    break;
                case "ORDERING":
                    s.setRemainingTime(s.getRemainingTime() - 1);
                    if (s.getRemainingTime() <= 0) {
                        resourceManager.getWindowById(s.getTargetId()).getStudentQueue().poll();
                        if (resourceManager.hasEmptySeat()) {
                            resourceManager.tryToOccupySeat(s);
                            s.setStatus("SEEK_SEAT");
                        } else {
                            s.setStatus("WAITING_FOR_SEAT");
                        }
                    }
                    break;
                case "WAITING_FOR_SEAT":
                    if (resourceManager.hasEmptySeat()) {
                        resourceManager.tryToOccupySeat(s);
                        s.setStatus("SEEK_SEAT");
                    }
                    break;
                case "SEEK_SEAT":
                    Seat mySeat = resourceManager.findSeatByStudentId(s.getId());
                    if (mySeat != null) {
                        double[] pos = resourceManager.getSeatCoordinate(mySeat.getId());
                        if (moveEngine.moveTowards(s, pos[0], pos[1], config.getMoveSpeed())) {
                            s.setStatus("EATING");
                            s.setRemainingTime(moveEngine.calculateNormalTime(config.getEatingMu(), config.getEatingSigma(), 300));
                        }
                    }
                    break;
                case "EATING":
                    s.setRemainingTime(s.getRemainingTime() - 1);
                    if (s.getRemainingTime() <= 0) {
                        resourceManager.releaseSeat(s.getId());
                        s.setStatus("LEAVING");
                    }
                    break;
                case "LEAVING":
                    if (moveEngine.moveTowards(s, config.DOOR_X, config.DOOR_Y, config.getMoveSpeed())) {
                        students.remove(s);
                    }
                    break;
            }
        }
    }
}