package com.bjtu.dining_simulation.dto;

import lombok.Data;

@Data
public class StartConfigDTO {
    private int studentCount;
    private int windowCount;
    private int simDurationTick;
    private int seatCount;
    private int maxQueueLength;
    private int orderingTime;
    private int eatingTime;
}
