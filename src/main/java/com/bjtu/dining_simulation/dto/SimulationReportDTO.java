package com.bjtu.dining_simulation.dto;

import lombok.Data;

@Data
public class SimulationReportDTO {
    private String simId;
    private String createdAt;
    private String score;
    private String suggestion;
    private Summary summary;

    @Data
    public static class Summary {
        private long avgWaitTime;
        private long avgSeatWaitTime;
        private double seatTurnoverRate;
        private double lossRate;
        private int maxCongestion;
        private int generated;
        private int finished;
        private int lost;
    }
}
