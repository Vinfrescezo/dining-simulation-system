package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.model.*;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Getter // 这将自动生成 getWindows() 和 getSeats()
public class ResourceManager {
    private final List<Window> windows = new ArrayList<>(Arrays.asList(
        new Window("窗口-A", 1.0, 10, new LinkedList<>()),
        new Window("窗口-B", 1.2, 10, new LinkedList<>())
    ));

    private final List<Seat> seats = new ArrayList<>();
    private final Random random = new Random();

    public ResourceManager() {
        for (int i = 1; i <= 50; i++) {
            seats.add(new Seat("座位-" + i, false, null, 0));
        }
    }

    // --- 业务辅助方法 ---
    public boolean isAllWindowFull() {
        return windows.stream().allMatch(w -> w.getStudentQueue().size() >= w.getMaxQueueCapacity());
    }

    public Window getRandomWindow() {
        return windows.get(random.nextInt(windows.size()));
    }

    public Window getWindowById(String id) {
        return windows.stream().filter(w -> w.getId().equals(id)).findFirst().orElse(null);
    }

    public boolean isFirstInQueue(Window w, Student s) {
        return w != null && w.getStudentQueue().peek() != null && 
               w.getStudentQueue().peek().getId().equals(s.getId());
    }

    public boolean hasEmptySeat() {
        return seats.stream().anyMatch(seat -> !seat.isOccupied());
    }

    public void tryToOccupySeat(Student s) {
        seats.stream().filter(seat -> !seat.isOccupied()).findFirst().ifPresent(seat -> {
            seat.setOccupied(true);
            seat.setStudentId(s.getId());
            s.setTargetId(seat.getId());
        });
    }

    public void releaseSeat(String studentId) {
        seats.stream().filter(s -> studentId.equals(s.getStudentId())).findFirst().ifPresent(s -> {
            s.setOccupied(false);
            s.setStudentId(null);
        });
    }

    public Seat findSeatByStudentId(String studentId) {
        return seats.stream().filter(s -> studentId.equals(s.getStudentId())).findFirst().orElse(null);
    }

    public double[] getSeatCoordinate(String seatId) {
        int idx = Integer.parseInt(seatId.split("-")[1]) - 1;
        return new double[]{ 110 + (idx % 10 * 68), 280 + (idx / 10 * 65) };
    }
}