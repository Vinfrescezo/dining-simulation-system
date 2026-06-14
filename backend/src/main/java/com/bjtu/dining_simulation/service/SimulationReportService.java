package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.dto.SimulationReportDTO;
import com.bjtu.dining_simulation.model.Window;
import com.bjtu.dining_simulation.repository.SimulationHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulationReportService {

    @Autowired private SimulationService simService;
    @Autowired private SimulationHistoryRepository historyRepository;

    /**
     * 生成当前阶段报告。
     * 这个方法可以在仿真运行中随时调用，不会写入历史数据库。
     */
    public SimulationReportDTO generateReport() {
        return buildReport(simService.isFinished());
    }

    /**
     * 生成最终报告，并且只保存一次到 H2 历史库。
     */
    public SimulationReportDTO generateAndSaveFinalReport() {
        SimulationReportDTO report = buildReport(true);
        if (simService.markReportSaved()) {
            historyRepository.saveReport(report);
        }
        return report;
    }

    public List<Map<String, Object>> listRecentReports(int limit) {
        return historyRepository.listRecentReports(limit);
    }

    public Map<String, Object> generateRealtimeDiagnosis() {
        double avgWaitTime = simService.getRealtimeAvgQueueTime();
        double avgSeatWaitTime = simService.getRealtimeAvgSeatWaitTime();
        double lossRate = simService.getLossRate();
        int maxSeatWaiting = simService.getMaxSeatWaiting();
        String type = diagnoseBottleneck(avgWaitTime, avgSeatWaitTime, lossRate, maxSeatWaiting);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bottleneckType", type);
        map.put("bottleneckReason", buildBottleneckReason(type, avgWaitTime, avgSeatWaitTime, lossRate, maxSeatWaiting));
        map.put("topHotWindowSuggestion", generateHotWindowSuggestion());
        return map;
    }

    private SimulationReportDTO buildReport(boolean finalReport) {
        SimulationReportDTO report = new SimulationReportDTO();
        int finishedCount = simService.getFinishedCount();
        int generatedCount = simService.getGeneratedCount();
        int lostCount = simService.getLostCount();
        int totalSeats = simService.getSeats().size();
        int servedCount = simService.getWindows().stream().mapToInt(Window::getServed).sum();

        double avgWaitTime = simService.getRealtimeAvgQueueTime();
        double avgSeatWaitTime = simService.getRealtimeAvgSeatWaitTime();
        double avgEatingTime = simService.getRealtimeAvgEatingTime();
        double lossRate = simService.getLossRate();
        double seatTurnoverRate = totalSeats > 0 ? (double) finishedCount / totalSeats : 0;
        String bottleneckType = diagnoseBottleneck(avgWaitTime, avgSeatWaitTime, lossRate, simService.getMaxSeatWaiting());
        String bottleneckReason = buildBottleneckReason(bottleneckType, avgWaitTime, avgSeatWaitTime, lossRate, simService.getMaxSeatWaiting());
        String hotWindowSuggestion = generateHotWindowSuggestion();

        ScoreResult scoreResult = computeScore(lossRate, avgWaitTime, avgSeatWaitTime, simService.getMaxSeatWaiting());
        report.setNumericScore(scoreResult.numericScore);
        report.setGradeLevel(scoreResult.gradeLevel);
        report.setDeductionReason(scoreResult.deductionReason);
        report.setScore(scoreResult.numericScore + "分 · " + scoreResult.gradeLevel);
        report.setBottleneckType(bottleneckType);
        report.setBottleneckReason(bottleneckReason);
        report.setTopHotWindowSuggestion(hotWindowSuggestion);
        report.setSuggestion(generateSuggestion(avgWaitTime, avgSeatWaitTime, lossRate,
                simService.getMaxSeatWaiting(), simService.getQueueLostCount(), simService.getSeatAbandonCount(),
                bottleneckType, hotWindowSuggestion));
        report.setSimId(simService.getCurrentSimId());
        report.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        report.setSource("backend");
        report.setReportType(finalReport ? "最终报告" : "阶段性报告");
        report.setReportNote(finalReport
                ? "本报告在仿真结束后生成，统计数据为完整仿真结果。"
                : String.format("本报告在仿真运行到第 %d 秒（Tick）时生成，统计数据为当前阶段累计结果。", simService.getGlobalTickCounter()));

        SimulationReportDTO.Summary summary = new SimulationReportDTO.Summary();
        summary.setAvgWaitTime(Math.round(avgWaitTime));
        summary.setAvgSeatWaitTime(Math.round(avgSeatWaitTime));
        summary.setAvgEatingTime(Math.round(avgEatingTime));
        summary.setSeatTurnoverRate(Math.round(seatTurnoverRate * 100.0) / 100.0);
        summary.setLossRate(Math.round(lossRate * 10000.0) / 10000.0);
        summary.setMaxCongestion(simService.getMaxCongestion());
        summary.setMaxSeatWaiting(simService.getMaxSeatWaiting());
        summary.setGenerated(generatedCount);
        summary.setFinished(finishedCount);
        summary.setLost(lostCount);
        summary.setQueueLost(simService.getQueueLostCount());
        summary.setSeatAbandoned(simService.getSeatAbandonCount());
        summary.setServed(servedCount);
        summary.setBottleneckType(bottleneckType);
        report.setSummary(summary);

        report.setTrend(simService.getTrendHistory().stream().map(item -> {
            SimulationReportDTO.TrendPoint p = new SimulationReportDTO.TrendPoint();
            p.setTick(((Number)item.getOrDefault("tick", 0)).intValue());
            p.setActiveCount(((Number)item.getOrDefault("activeCount", 0)).intValue());
            p.setOccupiedSeats(((Number)item.getOrDefault("occupiedSeats", 0)).longValue());
            p.setWaitingSeatCount(((Number)item.getOrDefault("waitingSeatCount", 0)).longValue());
            p.setAvgWaitTime(((Number)item.getOrDefault("avgWaitTime", 0)).doubleValue());
            p.setAvgSeatWaitTime(((Number)item.getOrDefault("avgSeatWaitTime", 0)).doubleValue());
            return p;
        }).collect(Collectors.toList()));

        List<SimulationReportDTO.WindowPerformance> windows = new ArrayList<>();
        for (Window w : simService.getWindows()) {
            List<Integer> samples = simService.getQueueLengthSamples().getOrDefault(w.getId(), Collections.emptyList());
            double avgQueueLength = samples.isEmpty() ? 0 : samples.stream().mapToInt(Integer::intValue).average().orElse(0);
            SimulationReportDTO.WindowPerformance wp = new SimulationReportDTO.WindowPerformance();
            wp.setId(w.getId());
            wp.setDishName(w.getDishName());
            wp.setPopularityRank(w.getPopularityRank());
            wp.setPopularityScore(Math.round(w.getPopularityScore() * 100.0) / 100.0);
            wp.setAvgQueueLength(Math.round(avgQueueLength * 100.0) / 100.0);
            wp.setAvgWaitTime(Math.round(w.getAvgQueueTimeIncludingCurrent(simService.getGlobalTickCounter()) * 100.0) / 100.0);
            wp.setPeakQueueLength(w.getPeakQueueLength());
            wp.setTotalServedCount(w.getServed());
            windows.add(wp);
        }
        report.setWindowPerformance(windows);
        return report;
    }

    private String diagnoseBottleneck(double avgWaitTime, double avgSeatWaitTime, double lossRate, int maxSeatWaiting) {
        // 打饭时长 28 秒/人，N 个窗口理论吞吐 ≈ N/28 人/秒；
        // 阈值按"明显排队感"设定：排队 >3 分钟 或 流失 >5%
        boolean windowPressure = lossRate > 0.05 || avgWaitTime > 180;
        // 等座阈值：找座 >2 分钟 或 等座峰值 >30 人
        boolean seatPressure = avgSeatWaitTime > 120 || maxSeatWaiting > 30;
        if (windowPressure && seatPressure) return "窗口与座位双重瓶颈";
        if (windowPressure) return "窗口服务瓶颈";
        if (seatPressure) return "座位资源瓶颈";
        return "运行基本均衡";
    }

    private String formatDuration(double tickSeconds) {
        int totalSeconds = (int) Math.round(Math.max(0, tickSeconds));
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes <= 0) return seconds + "秒";
        return String.format("%d分%02d秒", minutes, seconds);
    }

    private String buildBottleneckReason(String type, double avgWaitTime, double avgSeatWaitTime, double lossRate, int maxSeatWaiting) {
        return switch (type) {
            case "窗口服务瓶颈" -> String.format("平均排队约 %s，窗口流失率 %.1f%%，学生主要卡在打饭窗口的队列上。", formatDuration(avgWaitTime), lossRate * 100);
            case "座位资源瓶颈" -> String.format("平均找座约 %s，找座峰值 %d 人，打完饭后找座是主要压力。", formatDuration(avgSeatWaitTime), maxSeatWaiting);
            case "窗口与座位双重瓶颈" -> String.format("平均排队 %s、平均找座 %s，窗口与座位均承压。", formatDuration(avgWaitTime), formatDuration(avgSeatWaitTime));
            default -> "排队、找座、流失三项指标都处于较低区间，当前资源配置基本均衡。";
        };
    }

    private String generateHotWindowSuggestion() {
        Optional<Window> hot = simService.getWindows().stream()
                .filter(w -> w.getPopularityRank() <= 3)
                .max(Comparator.comparingDouble(w -> w.getAvgQueueTimeIncludingCurrent(simService.getGlobalTickCounter()) + w.getPeakQueueLength() * 4.0));
        if (hot.isPresent()) {
            Window w = hot.get();
            double avgWait = w.getAvgQueueTimeIncludingCurrent(simService.getGlobalTickCounter());
            int peak = w.getPeakQueueLength();
            if (avgWait > 240 || peak >= 18) {
                return String.format("%s 长期高负载（平均等待 %s、峰值队长 %d 人），建议高峰期增设同类备餐点或安排预制分流。",
                        w.getDishName(), formatDuration(avgWait), peak);
            }
        }
        return "热门菜品窗口负载暂未明显失衡，可继续观察多轮仿真结果。";
    }

    /** 评分内部封装：百分制 + 五级评级 + 主要扣分项 */
    private static class ScoreResult {
        int numericScore;
        String gradeLevel;
        String deductionReason;
    }

    /**
     * 百分制评分：
     *   基准 100 分，按四项指标分别扣分（排队时长、等座时长、流失率、等座峰值），
     *   最终得分落在五级评级区间内：
     *     90—100 优秀；75—89 良好；60—74 基本可控；40—59 偏拥挤；0—39 严重拥挤
     * 阈值基于"打饭 28 秒/人 + 用餐 8 分钟"模型校准
     */
    private ScoreResult computeScore(double lossRate, double avgWaitTime, double avgSeatWaitTime, int maxSeatWaiting) {
        int score = 100;
        List<String> deductions = new ArrayList<>();

        // 排队时长扣分：每超 60 秒扣 4 分，最多扣 30
        if (avgWaitTime > 60) {
            int d = (int) Math.min(30, Math.round((avgWaitTime - 60) / 60.0 * 4));
            if (d > 0) { score -= d; deductions.add(String.format("平均排队 %s（-%d）", formatDuration(avgWaitTime), d)); }
        }
        // 找座时长扣分：每超 30 秒扣 3 分，最多扣 20
        if (avgSeatWaitTime > 30) {
            int d = (int) Math.min(20, Math.round((avgSeatWaitTime - 30) / 30.0 * 3));
            if (d > 0) { score -= d; deductions.add(String.format("平均找座 %s（-%d）", formatDuration(avgSeatWaitTime), d)); }
        }
        // 窗口流失扣分：每 1% 扣 2 分，最多扣 30
        if (lossRate > 0.005) {
            int d = (int) Math.min(30, Math.round(lossRate * 100 * 2));
            if (d > 0) { score -= d; deductions.add(String.format("窗口流失 %.1f%%（-%d）", lossRate * 100, d)); }
        }
        // 等座峰值扣分：每超过 20 人扣 1.5 分，最多扣 20
        if (maxSeatWaiting > 20) {
            int d = (int) Math.min(20, Math.round((maxSeatWaiting - 20) * 1.5));
            if (d > 0) { score -= d; deductions.add(String.format("等座峰值 %d 人（-%d）", maxSeatWaiting, d)); }
        }
        score = Math.max(0, Math.min(100, score));

        ScoreResult r = new ScoreResult();
        r.numericScore = score;
        if (score >= 90) r.gradeLevel = "优秀";
        else if (score >= 75) r.gradeLevel = "良好";
        else if (score >= 60) r.gradeLevel = "基本可控";
        else if (score >= 40) r.gradeLevel = "偏拥挤";
        else r.gradeLevel = "严重拥挤";
        r.deductionReason = deductions.isEmpty() ? "各项指标均处于较低区间，未有明显扣分。" : String.join("；", deductions) + "。";
        return r;
    }

    private String generateSuggestion(double avgWaitTime, double avgSeatWaitTime, double lossRate, int maxSeatWaiting,
                                      int queueLostCount, int seatAbandonCount, String bottleneckType, String hotWindowSuggestion) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("当前主要瓶颈：" + bottleneckType + "。");

        // 窗口侧建议（基于流失率和排队时长）
        if (lossRate > 0.15) {
            suggestions.add(String.format("窗口流失率达 %.1f%%，窗口承载严重不足，建议增加 2—3 个开放窗口或缩短打饭时长。", lossRate * 100));
        } else if (lossRate > 0.05 || avgWaitTime > 240) {
            suggestions.add("排队压力偏高，建议增加 1—2 个窗口或优化窗口分流引导。");
        } else if (avgWaitTime > 120) {
            suggestions.add("排队时间略长，可在高峰时段临时增开窗口。");
        }

        // 座位侧建议（仅基于实际等座时长和峰值）
        if (avgSeatWaitTime > 180 || maxSeatWaiting > 60) {
            suggestions.add("座位资源不足，建议增加座位或引导错峰就餐。");
        } else if (avgSeatWaitTime > 90 || maxSeatWaiting > 30) {
            suggestions.add("找座时间偏长，建议提高翻台效率或在高峰期增设临时餐位。");
        }

        if (hotWindowSuggestion != null && !hotWindowSuggestion.contains("暂未明显")) {
            suggestions.add(hotWindowSuggestion);
        }

        if (suggestions.size() == 1) {
            suggestions.add("当前窗口和座位配置基本合理，可保留本次数据用于多轮方案对比。");
        }
        return String.join(" ", suggestions).trim();
    }
}
