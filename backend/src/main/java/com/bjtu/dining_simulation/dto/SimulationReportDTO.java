package com.bjtu.dining_simulation.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationReportDTO {
    private String simId;
    private String createdAt;
    /** 综合评分展示文本，例如：84分 · 良好 */
    private String score;
    /** 百分制得分，0—100 */
    private int numericScore;
    /** 五级制评级：优秀 / 良好 / 基本可控 / 偏拥挤 / 严重拥挤 */
    private String gradeLevel;
    /** 主要扣分项，用于解释评分来源 */
    private String deductionReason;
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
    @JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrendPoint {
        private int tick;
        private int activeCount;
        private long occupiedSeats;
        private long waitingSeatCount;
        private double avgWaitTime;
        private double avgSeatWaitTime;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WindowPerformance {
        private String id;
        private String dishName;
        private int popularityRank;
        private double popularityScore;
        private int baseServiceTimeSeconds;
        private double avgQueueLength;
        private double avgWaitTime;
        private int peakQueueLength;
        private int totalServedCount;
    }
}
