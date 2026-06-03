package com.bjtu.dining_simulation.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class StartResponseDTO {
    private int code;
    private String status;
    private String msg;
    private String message;
    private String simId;
    private Object data;
    private int totalTicks;
    private Map<String, Object> layout;
    private List<SimulationEventDTO> events;
    private SimulationReportDTO report;
}
