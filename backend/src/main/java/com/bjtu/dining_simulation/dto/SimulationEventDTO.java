package com.bjtu.dining_simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SimulationEventDTO {
    private int tick;
    private String type;
    private String studentId;
    private String targetId;
    private double x;
    private double y;
}
