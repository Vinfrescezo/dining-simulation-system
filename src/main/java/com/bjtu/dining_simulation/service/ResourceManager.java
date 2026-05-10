package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.model.*;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Getter
public class ResourceManager {
    private final List<Window> windows = new ArrayList<>();
    private final List<Seat> seats = new ArrayList<>();
    private final Random random = new Random();

    private final double CANVAS_WIDTH = 930;
    private final double CANVAS_HEIGHT = 620;
    private final Map<String, Double> serviceArea = Map.of("x", 188.0, "y", 46.0, "w", 676.0, "h", 146.0);
    private final Map<String, Double> waitingZone = Map.of("x", 56.0, "y", 320.0, "w", 102.0, "h", 86.0);
    private final Map<String, Double> seatArea = Map.of("x", 174.0, "y", 222.0, "w", 664.0, "h", 330.0);

    private final double AREA_X = 174.0;
    private final double AREA_Y = 222.0;
    private final double AREA_W = 664.0;
    private final double AREA_H = 330.0;
    
    private final double INNER_PADDING_X = 18.0;
    private final double INNER_PADDING_Y = 18.0;
    private final double NOMINAL_GAP_X = 12.0;
    private final double NOMINAL_GAP_Y = 12.0;

    public ResourceManager() {
        initResources(10, 240); // 默认初始化
    }

    private int[] chooseBestGrid(int tableCount, double areaW, double areaH) {
        int bestCols = 4;
        double bestScore = -Double.MAX_VALUE;

        for (int cols = 4; cols <= 12; cols++) {
            int rows = (int) Math.ceil((double) tableCount / cols);
            double cellW = areaW / cols;
            double cellH = areaH / rows;
            double score = Math.min(cellW, cellH * 1.18);
            if (score > bestScore) {
                bestScore = score;
                bestCols = cols;
            }
        }
        return new int[]{bestCols, (int) Math.ceil((double) tableCount / bestCols)};
    }

    public void initResources(int windowCount, int seatCount) {
        this.windows.clear();
        this.seats.clear();

        // 1. 窗口初始化;
        double usableW = serviceArea.get("w") - 84;
        double gap = Math.min(88, Math.max(48, usableW / Math.max(windowCount - 1, 1)));
        double centeredStartX = serviceArea.get("x") + serviceArea.get("w") / 2 - ((windowCount - 1) * gap) / 2;

        for (int i = 0; i < windowCount; i++) {
            double x = (windowCount == 1) ? serviceArea.get("x") + serviceArea.get("w") / 2 : centeredStartX + i * gap;
            // 窗口 Y 固定为 96, 队列起始 Y 为 152, 间距 12
            Window w = new Window("W" + (i + 1), x, 96.0, new LinkedList<>());
            this.windows.add(w);
        }

        // 2. 座位与桌子初始化
        int tableCount = (int) Math.ceil(seatCount / 4.0);
        int[] grid = chooseBestGrid(tableCount, seatArea.get("w"), seatArea.get("h"));
        int tableCols = grid[0];
        int tableRows = grid[1];

        double innerPaddingX = 18, innerPaddingY = 18;
        double nominalGapX = 12, nominalGapY = 12;
        double usableW_Seat = seatArea.get("w") - innerPaddingX * 2 - nominalGapX * (tableCols - 1);
        double usableH_Seat = seatArea.get("h") - innerPaddingY * 2 - nominalGapY * (tableRows - 1);
        double cellW = usableW_Seat / tableCols;
        double cellH = usableH_Seat / tableRows;

        double tableW = Math.min(38, Math.max(24, cellW * 0.42));
        double tableH = Math.min(28, Math.max(18, cellH * 0.32));
        double chairGap = Math.min(14, Math.max(8, Math.min(cellW, cellH) * 0.16));

        for (int i = 0; i < seatCount; i++) {
            int tableIdx = i / 4;
            int seatIdx = i % 4;
            int col = tableIdx % tableCols;
            int row = tableIdx / tableCols;

            double tx = seatArea.get("x") + innerPaddingX + col * (cellW + nominalGapX) + cellW / 2;
            double ty = seatArea.get("y") + innerPaddingY + row * (cellH + nominalGapY) + cellH / 2;

            double sx = tx, sy = ty;
            if (seatIdx == 0) sy = ty - tableH / 2 - chairGap;      // top
            else if (seatIdx == 1) sx = tx + tableW / 2 + chairGap; // right
            else if (seatIdx == 2) sy = ty + tableH / 2 + chairGap; // bottom
            else if (seatIdx == 3) sx = tx - tableW / 2 - chairGap; // left

            this.seats.add(new Seat("S" + (i + 1), false, null, sx, sy));
        }
    }

    public double[] getQueueSpot(Window window, int queueIndex) {
        return new double[]{ window.getX(), 152 + queueIndex * 12 };
    }

    public double[] getWaitingSpot(int index) {
        double paddingX = 14, paddingY = 18;
        double usableW = Math.max(32, waitingZone.get("w") - paddingX * 2);
        double usableH = Math.max(30, waitingZone.get("h") - paddingY * 2);
        int cols = Math.max(3, Math.min(4, (int) Math.floor(usableW / 20.0)));
        int rows = (int) Math.ceil((index + 1.0) / cols);
        double gapX = cols > 1 ? usableW / (cols - 1) : 0;
        double gapY = rows > 1 ? Math.min(16, usableH / Math.max(1, rows - 1)) : 0;

        return new double[]{
            waitingZone.get("x") + paddingX + (index % cols) * gapX,
            waitingZone.get("y") + paddingY + Math.floor(index / (double)cols) * gapY
        };
    }

    // --- 辅助方法 ---
    public boolean isAllWindowFull() { 
        return windows.stream().allMatch(w -> w.getStudentQueue().size() >= 30); 
    }
    
    public Window getRandomWindow() { 
        return windows.get(random.nextInt(windows.size())); 
    }
    
    public Window getWindowById(String id) { 
        return windows.stream().filter(w -> w.getId().equals(id)).findFirst().orElse(null); 
    }
    
    public boolean isFirstInQueue(Window w, Student s) { 
        return w != null && !w.getStudentQueue().isEmpty() && w.getStudentQueue().peek().getId().equals(s.getId()); 
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
    
    public Window getShortestQueueWindow() {
    Window shortest = windows.get(0);
    for (Window w : windows) {
        if (w.getStudentQueue().size() < shortest.getStudentQueue().size()) {
            shortest = w;
        }
    }
    return shortest;
}
}