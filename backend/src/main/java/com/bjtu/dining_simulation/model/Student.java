package com.bjtu.dining_simulation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(of = "id")
@AllArgsConstructor
@NoArgsConstructor
public class Student {
    private String id;
    private double x;
    private double y;
    private String status;   // PATHFINDING, QUEUEING, ORDERING, WAITING_FOR_SEAT, SEEK_SEAT, EATING, LEAVING
    private String targetId; // target window or seat id

    private double targetX;
    private double targetY;

    private int remainingTime;

    // Real per-student timestamps. Queue/seat-wait metrics are calculated from these values.
    private int queueStartTick = -1;
    private int orderStartTick = -1;
    private int queueTime = 0;
    private Integer seatWaitStartTick;
    private int seatWaitEndTick = -1;

    // 打饭完成后先在座位区短暂寻座，找不到再进入端盘等座区。
    private int seatSearchStartTick = -1;
    private int seatSearchDurationTick = 0;
    private String reservedSeatId;

    private int totalWaitTime = 0;
    private int totalSeatWaitTime = 0;

    // Real sampled ordering/eating durations, not fixed demo values.
    private int orderingDuration = 0;
    private int eatingDuration = 0;

    // Some students prefer one window, but final choice still considers queue length and distance.
    private String preferredWindowId;
    private String chosenWindowId;

    public Student(String id, double x, double y, String status, String targetId, int remainingTime) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.status = status;
        this.targetId = targetId;
        this.remainingTime = remainingTime;
    }
}
