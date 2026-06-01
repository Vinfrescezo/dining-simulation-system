package com.bjtu.dining_simulation.dto;

import lombok.Data;
import java.util.List;

@Data
public class SimulationReportDTO {
    private String simId;
    private String createdAt;
    private String score;
    private String suggestion;
    private String bottleneckType;
    private String bottleneckReason;
    private String topHotWindowSuggestion;
    private String source = "backend";
    /** 报告类型：阶段性报告 / 最终报告 */
    private String reportType;
    /** 报告说明：提醒用户当前报告是运行中快照还是完整结果 */
    private String reportNote;
    private Summary summary;
    private List<TrendPoint> trend;
    private List<WindowPerformance> windowPerformance;

    @Data
    public static class Summary {
        private long avgWaitTime;
        private long avgSeatWaitTime;
        private long avgEatingTime;
        private double seatTurnoverRate;
        private double lossRate;
        private int maxCongestion;
        private int maxSeatWaiting;
        private int generated;
        private int finished;
        private int lost;
        private int queueLost;
        private int seatAbandoned;
        private int served;
        private String bottleneckType;
    }

    @Data
    public static class TrendPoint {
        private int tick;
        private int activeCount;
        private long occupiedSeats;
        private long waitingSeatCount;
        private double avgWaitTime;
        private double avgSeatWaitTime;
    }

    @Data
    public static class WindowPerformance {
        private String id;
        private String dishName;
        private int popularityRank;
        private double popularityScore;
        private double avgQueueLength;
        private double avgWaitTime;
        private int peakQueueLength;
        private int totalServedCount;
    }
}
