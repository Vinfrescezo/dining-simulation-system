package com.bjtu.dining_simulation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Seat {
    private String id;
    private boolean isOccupied;
    private String studentId;
    private double x;
    private double y;

    private boolean reserved;
    private String reservedBy;

    private String tableId;
    private String position; // top, right, bottom, left
    private int occupancyTicks;

    public Seat(String id, boolean isOccupied, String studentId, double x, double y, boolean reserved, String reservedBy) {
        this.id = id;
        this.isOccupied = isOccupied;
        this.studentId = studentId;
        this.x = x;
        this.y = y;
        this.reserved = reserved;
        this.reservedBy = reservedBy;
    }
}
