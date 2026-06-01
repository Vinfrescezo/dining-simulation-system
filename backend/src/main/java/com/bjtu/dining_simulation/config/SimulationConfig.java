package com.bjtu.dining_simulation.config;

import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class SimulationConfig {
    public final double CANVAS_WIDTH = 1600.0;
    public final double CANVAS_HEIGHT = 900.0;

    public final double DOOR_X = 70.0;
    public final double DOOR_Y = CANVAS_HEIGHT - 48.0;
    public final double EXIT_X = CANVAS_WIDTH - 70.0;
    public final double EXIT_Y = CANVAS_HEIGHT - 48.0;

    private final double orderingMu = 18.0;
    private final double orderingSigma = 5.0;
    private final double eatingMu = 480.0;
    private final double eatingSigma = 90.0;

    private final double moveSpeed = 14.0;
    private int maxQueueLength = 40;

    private int maxSeatWaitCapacity = 80;
    private int maxSeatWaitTick = 240;

    private final double arrivalPeakCenter = 0.32;
    private final double arrivalPeakSigma = 0.12;
}
