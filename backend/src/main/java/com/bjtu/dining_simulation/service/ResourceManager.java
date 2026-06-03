package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.model.*;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Getter
public class ResourceManager {
    private final List<Window> windows = new ArrayList<>();
    private final List<Seat> seats = new ArrayList<>();
    private final Random random = new Random();

    @Autowired private SimulationConfig simulationConfig;

    private final double CANVAS_WIDTH = 1600;
    private final double CANVAS_HEIGHT = 900;
    private final Map<String, Double> serviceArea = Map.of("x", 110.0, "y", 22.0, "w", 1380.0, "h", 255.0);
    private final Map<String, Double> waitingZone = Map.of("x", 18.0, "y", 300.0, "w", 80.0, "h", 500.0);
    private final Map<String, Double> seatArea = Map.of("x", 110.0, "y", 300.0, "w", 1290.0, "h", 500.0);

    private static final Map<String, String> DIAGONAL = Map.of(
            "top", "bottom", "bottom", "top", "left", "right", "right", "left"
    );
    private static final String[] POSITION_NAMES = {"top", "right", "bottom", "left"};

    private static class DishProfile {
        final String dish;
        final int rank;
        final double popularity;
        final double serviceWeight;

        DishProfile(String dish, int rank, double popularity, double serviceWeight) {
            this.dish = dish;
            this.rank = rank;
            this.popularity = popularity;
            this.serviceWeight = serviceWeight;
        }
    }

    private static final List<DishProfile> HOT_DISHES = List.of(
            new DishProfile("麻辣香锅", 1, 5.0, 1.8),
            new DishProfile("炙烤五花肉", 2, 4.7, 1.4),
            new DishProfile("土豆泥拌饭", 3, 4.3, 0.9),
            new DishProfile("北京烤鸭", 4, 4.1, 1.6),
            new DishProfile("云南米线", 5, 3.8, 0.8),
            new DishProfile("兰州拉面", 6, 3.6, 0.7),
            new DishProfile("黄焖鸡米饭", 7, 3.3, 1.0),
            new DishProfile("番茄牛腩饭", 8, 3.0, 1.1),
            new DishProfile("鸡排饭", 9, 2.8, 0.8),
            new DishProfile("轻食沙拉", 10, 2.2, 0.5),
            new DishProfile("自选小炒", 11, 2.0, 1.3),
            new DishProfile("盖浇饭", 12, 1.8, 0.6)
    );

    public ResourceManager() {
        initResources(10, 240);
    }

    private int[] chooseBestGrid(int tableCount, double areaW, double areaH) {
        int bestCols = 4;
        double bestScore = -Double.MAX_VALUE;
        for (int cols = 4; cols <= 14; cols++) {
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

        double saX = serviceArea.get("x"), saW = serviceArea.get("w"), saY = serviceArea.get("y");
        double usableW = saW - 140;
        double gap = Math.min(130, Math.max(68, usableW / Math.max(windowCount - 1, 1)));
        double centeredStartX = saX + saW / 2 - ((windowCount - 1) * gap) / 2;
        double winY = saY + 78;

        for (int i = 0; i < windowCount; i++) {
            double x = (windowCount == 1) ? saX + saW / 2 : centeredStartX + i * gap;
            Window w = new Window("W" + (i + 1), x, winY, new LinkedList<>());
            DishProfile profile = HOT_DISHES.get(i % HOT_DISHES.size());
            w.setDisplayName("W" + (i + 1) + " " + profile.dish);
            w.setDishName(profile.dish);
            w.setPopularityRank(profile.rank);
            w.setPopularityScore(profile.popularity);
            w.setPreferenceWeight(profile.popularity);
            w.setServiceWeight(profile.serviceWeight + random.nextDouble() * 0.25);
            this.windows.add(w);
        }

        int tableCount = (int) Math.ceil(seatCount / 4.0);
        int[] grid = chooseBestGrid(tableCount, seatArea.get("w"), seatArea.get("h"));
        int tableCols = grid[0];
        int tableRows = grid[1];

        double innerPaddingX = 28, innerPaddingY = 30;
        double nominalGapX = 12, nominalGapY = 12;
        double usableWSeat = seatArea.get("w") - innerPaddingX * 2 - nominalGapX * (tableCols - 1);
        double usableHSeat = seatArea.get("h") - innerPaddingY * 2 - nominalGapY * (tableRows - 1);
        double cellW = usableWSeat / tableCols;
        double cellH = usableHSeat / tableRows;

        double tableW = Math.min(48, Math.max(26, cellW * 0.44));
        double tableH = Math.min(34, Math.max(20, cellH * 0.34));
        double chairGap = Math.min(16, Math.max(9, Math.min(cellW, cellH) * 0.17));

        for (int i = 0; i < seatCount; i++) {
            int tableIdx = i / 4;
            int seatIdx = i % 4;
            int col = tableIdx % tableCols;
            int row = tableIdx / tableCols;

            double tx = seatArea.get("x") + innerPaddingX + col * (cellW + nominalGapX) + cellW / 2;
            double ty = seatArea.get("y") + innerPaddingY + row * (cellH + nominalGapY) + cellH / 2;

            double sx = tx, sy = ty;
            if (seatIdx == 0) sy = ty - tableH / 2 - chairGap;
            else if (seatIdx == 1) sx = tx + tableW / 2 + chairGap;
            else if (seatIdx == 2) sy = ty + tableH / 2 + chairGap;
            else sx = tx - tableW / 2 - chairGap;

            String tableId = "T" + (tableIdx + 1);
            String position = POSITION_NAMES[seatIdx];
            Seat seat = new Seat("S" + (i + 1), false, null, sx, sy, false, null);
            seat.setTableId(tableId);
            seat.setPosition(position);
            this.seats.add(seat);
        }
    }

    public double[] getQueueSpot(Window window, int queueIndex) {
        double queueStartY = serviceArea.get("y") + 155;
        return new double[]{window.getX(), queueStartY + queueIndex * 12};
    }

    public double[] getWaitingSpot(int index) {
        double zx = waitingZone.get("x"), zy = waitingZone.get("y");
        double zw = waitingZone.get("w"), zh = waitingZone.get("h");
        double paddingX = 10, paddingY = 30;
        double usable = Math.max(40, zw - paddingX * 2);
        int cols = Math.max(2, Math.min(4, (int) Math.floor(usable / 20.0)));
        int row = index / cols;
        int col = index % cols;
        double gapX = cols > 1 ? usable / (cols - 1) : 0;
        double baseY = Math.min(zy + zh - 14, zy + paddingY + row * 22);
        double jitterX = Math.sin((index + 1) * 1.73) * 4;
        double jitterY = Math.cos((index + 1) * 2.11) * 4;
        double x = clamp(zx + paddingX + col * gapX + jitterX, zx + 10, zx + zw - 10);
        double y = clamp(baseY + jitterY, zy + paddingY, zy + zh - 14);
        return new double[]{x, y};
    }

    public double[] getWaitingRoamingSpot(int index, int tick) {
        double[] base = getWaitingSpot(index);
        double zx = waitingZone.get("x"), zy = waitingZone.get("y");
        double zw = waitingZone.get("w"), zh = waitingZone.get("h");
        double wanderX = Math.sin((tick + index * 17) / 18.0) * 5;
        double wanderY = Math.cos((tick + index * 23) / 22.0) * 5;
        double x = clamp(base[0] + wanderX, zx + 8, zx + zw - 8);
        double y = clamp(base[1] + wanderY, zy + 24, zy + zh - 14);
        return new double[]{x, y};
    }

    public double[] getSeatSearchRoamingSpot(Student student, int tick) {
        int hash = Math.abs(student.getId().hashCode());
        double laneTop = seatArea.get("y") + 42;
        double laneBottom = seatArea.get("y") + seatArea.get("h") - 42;
        double laneLeft = seatArea.get("x") + 35;
        double laneRight = seatArea.get("x") + seatArea.get("w") - 35;
        double phase = ((tick + hash % 91) % 160) / 160.0;
        double wave = Math.sin((tick + hash % 37) / 16.0);
        double x = laneLeft + phase * (laneRight - laneLeft);
        if (((hash / 7) % 2) == 0) x = laneRight - phase * (laneRight - laneLeft);
        double y = laneTop + ((hash % 7) / 6.0) * (laneBottom - laneTop) + wave * 18;
        return new double[]{clamp(x, laneLeft, laneRight), clamp(y, laneTop, laneBottom)};
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public Window getWindowById(String id) {
        return windows.stream().filter(w -> w.getId().equals(id)).findFirst().orElse(null);
    }

    public boolean hasEmptySeat() {
        return seats.stream().anyMatch(seat -> !seat.isOccupied() && !seat.isReserved());
    }

    public Seat reserveSeatForStudent(Student s) {
        Seat best = findBestSeat(s.getX(), s.getY());
        if (best == null) return null;
        best.setReserved(true);
        best.setReservedBy(s.getId());
        s.setTargetId(best.getId());
        s.setReservedSeatId(best.getId());
        return best;
    }

    private Seat findBestSeat(double studentX, double studentY) {
        double maxDist = Math.hypot(CANVAS_WIDTH, CANVAS_HEIGHT);
        double saTop = seatArea.get("y");
        double saH = seatArea.get("h");

        Map<String, Integer> tableOccupancy = new HashMap<>();
        for (Seat seat : seats) {
            tableOccupancy.merge(seat.getTableId(), (seat.isOccupied() || seat.isReserved()) ? 1 : 0, Integer::sum);
        }

        List<Seat> candidates = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (Seat seat : seats) {
            if (seat.isOccupied() || seat.isReserved()) continue;

            double dist = Math.hypot(seat.getX() - studentX, seat.getY() - studentY);
            double distScore = 1.0 - dist / maxDist;

            double rowRatio = (seat.getY() - saTop) / saH;
            double depthScore = 1.0 - rowRatio * 0.7;

            int occ = tableOccupancy.getOrDefault(seat.getTableId(), 0);
            double socialScore;
            if (occ == 0) {
                socialScore = 1.0;
            } else {
                boolean isDiag = seats.stream()
                        .filter(other -> other.getTableId().equals(seat.getTableId()))
                        .filter(other -> other.isOccupied() || other.isReserved())
                        .allMatch(other -> seat.getPosition().equals(DIAGONAL.get(other.getPosition())));
                socialScore = isDiag ? 0.7 : 0.3 - occ * 0.1;
            }

            double score = distScore * 0.35 + depthScore * 0.3 + socialScore * 0.35;
            candidates.add(seat);
            scores.add(score);
        }

        if (candidates.isEmpty()) return null;

        Integer[] indices = new Integer[candidates.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(scores.get(b), scores.get(a)));

        int topN = Math.min(candidates.size(), Math.max(3, (int) Math.ceil(candidates.size() * 0.15)));
        int pick = random.nextInt(topN);
        return candidates.get(indices[pick]);
    }

    public void markReservedSeatOccupied(Student s) {
        Seat seat = findReservedSeatByStudentId(s.getId());
        if (seat == null && s.getReservedSeatId() != null) {
            seat = seats.stream().filter(item -> s.getReservedSeatId().equals(item.getId())).findFirst().orElse(null);
        }
        if (seat != null) {
            seat.setReserved(false);
            seat.setReservedBy(null);
            seat.setOccupied(true);
            seat.setStudentId(s.getId());
            s.setTargetId(seat.getId());
            s.setReservedSeatId(null);
        }
    }

    public void releaseSeat(String studentId) {
        seats.stream().filter(s -> studentId.equals(s.getStudentId()) || studentId.equals(s.getReservedBy())).findFirst().ifPresent(s -> {
            s.setOccupied(false);
            s.setStudentId(null);
            s.setReserved(false);
            s.setReservedBy(null);
        });
    }

    public Seat findSeatByStudentId(String studentId) {
        return seats.stream().filter(s -> studentId.equals(s.getStudentId()) || studentId.equals(s.getReservedBy())).findFirst().orElse(null);
    }

    public Seat findReservedSeatByStudentId(String studentId) {
        return seats.stream().filter(s -> studentId.equals(s.getReservedBy())).findFirst().orElse(null);
    }

    public Map<String, Object> buildLayout() {
        Map<String, Object> layout = new LinkedHashMap<>();

        layout.put("width", CANVAS_WIDTH);
        layout.put("height", CANVAS_HEIGHT);
        layout.put("entrance", Map.of("x", simulationConfig.getDOOR_X(), "y", simulationConfig.getDOOR_Y()));
        layout.put("exit",     Map.of("x", simulationConfig.getEXIT_X(), "y", simulationConfig.getEXIT_Y()));
        layout.put("serviceArea", serviceArea);
        layout.put("seatArea", seatArea);
        layout.put("waitingZone", waitingZone);
        layout.put("exitLane", Map.of("x", 1430.0, "y", 300.0, "w", 44.0, "h", 500.0));

        double queueStartY = serviceArea.get("y") + 155.0;

        List<Map<String, Object>> winList = windows.stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("x",  w.getX());
            m.put("y",  w.getY());
            m.put("dishName",        w.getDishName());
            m.put("popularityRank",  w.getPopularityRank());
            m.put("popularityScore", w.getPopularityScore());
            m.put("queueX",      w.getX());
            m.put("queueStartY", queueStartY);
            m.put("queueGap",    12);
            return m;
        }).collect(Collectors.toList());
        layout.put("windows", winList);

        int seatCount = seats.size();
        int tableCount = (int) Math.ceil(seatCount / 4.0);
        int[] grid = chooseBestGrid(tableCount, seatArea.get("w"), seatArea.get("h"));
        int tableCols = grid[0];
        int tableRows = grid[1];
        double iPadX = 28, iPadY = 30, gapX = 12, gapY = 12;
        double uW = seatArea.get("w") - iPadX * 2 - gapX * (tableCols - 1);
        double uH = seatArea.get("h") - iPadY * 2 - gapY * (tableRows - 1);
        double cW = uW / tableCols, cH = uH / tableRows;
        double tW = Math.min(48, Math.max(26, cW * 0.44));
        double tH = Math.min(34, Math.max(20, cH * 0.34));

        List<Map<String, Object>> tableList = new ArrayList<>();
        for (int i = 0; i < tableCount; i++) {
            int col = i % tableCols;
            int row = i / tableCols;
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", "T" + (i + 1));
            t.put("x", seatArea.get("x") + iPadX + col * (cW + gapX) + cW / 2);
            t.put("y", seatArea.get("y") + iPadY + row * (cH + gapY) + cH / 2);
            t.put("w", tW);
            t.put("h", tH);
            tableList.add(t);
        }
        layout.put("tables", tableList);

        List<Map<String, Object>> seatList = seats.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("x",  s.getX());
            m.put("y",  s.getY());
            m.put("position", s.getPosition());
            m.put("tableId", s.getTableId());
            return m;
        }).collect(Collectors.toList());
        layout.put("seats", seatList);

        return layout;
    }

    public Window getBestQueueWindow(double startX, double startY, int maxQueueLength, String preferredWindowId) {
        Window best = null;
        double bestScore = Double.MAX_VALUE;
        for (Window w : windows) {
            int waitingQueueLength = w.getWaitingQueueLength();
            if (waitingQueueLength >= maxQueueLength) continue;
            double distance = Math.hypot(w.getX() - startX, w.getY() - startY);
            double preferenceBonus = w.getId().equals(preferredWindowId) ? 5.5 : w.getPreferenceWeight();
            double popularityAttraction = w.getPopularityScore() * 0.55;
            double rankPenalty = Math.max(0, w.getPopularityRank() - 1) * 0.03;
            double score = waitingQueueLength * 1.15
                    + distance * 0.010
                    + w.getServiceWeight() * 0.75
                    + rankPenalty
                    - preferenceBonus * 0.85
                    - popularityAttraction;
            if (score < bestScore) {
                bestScore = score;
                best = w;
            }
        }
        return best;
    }
}
