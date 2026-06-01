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

        report.setScore(calculateScore(lossRate, avgWaitTime, avgSeatWaitTime, simService.getMaxSeatWaiting()));
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
        boolean windowPressure = lossRate > 0.06 || avgWaitTime > 120 || simService.getQueueLostCount() > 0;
        boolean seatPressure = avgSeatWaitTime > 70 || maxSeatWaiting > 45 || simService.getSeatAbandonCount() > 0;
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
            case "窗口服务瓶颈" -> String.format("平均排队约 %s，流失率 %.1f%%，说明学生主要卡在窗口队列。", formatDuration(avgWaitTime), lossRate * 100);
            case "座位资源瓶颈" -> String.format("平均等座约 %s，等座峰值 %d 人，说明打饭后找座位是主要压力。", formatDuration(avgSeatWaitTime), maxSeatWaiting);
            case "窗口与座位双重瓶颈" -> String.format("平均排队约 %s、平均等座约 %s，窗口和座位都存在高峰压力。", formatDuration(avgWaitTime), formatDuration(avgSeatWaitTime));
            default -> "排队、等座和流失指标都处于较低区间，当前资源配置相对均衡。";
        };
    }

    private String generateHotWindowSuggestion() {
        Optional<Window> hot = simService.getWindows().stream()
                .filter(w -> w.getPopularityRank() <= 3)
                .max(Comparator.comparingDouble(w -> w.getAvgQueueTimeIncludingCurrent(simService.getGlobalTickCounter()) + w.getPeakQueueLength() * 4.0));
        if (hot.isPresent()) {
            Window w = hot.get();
            if (w.getAvgQueueTimeIncludingCurrent(simService.getGlobalTickCounter()) > 120 || w.getPeakQueueLength() > 18) {
                return String.format("%s 长期高负载，建议高峰期增设同类备餐点或安排预制分流。", w.getDishName());
            }
        }
        return "热门菜品窗口负载暂未明显失衡，可继续观察多轮仿真结果。";
    }

    private String calculateScore(double lossRate, double avgWaitTime, double avgSeatWaitTime, int maxSeatWaiting) {
        if (lossRate > 0.15 || avgWaitTime > 240 || avgSeatWaitTime > 180 || maxSeatWaiting > 120) return "极度拥挤";
        if (lossRate > 0.06 || avgWaitTime > 150 || avgSeatWaitTime > 90 || maxSeatWaiting > 60) return "偏拥挤";
        if (avgWaitTime > 80 || avgSeatWaitTime > 30 || maxSeatWaiting > 20) return "基本可控";
        return "运行顺畅";
    }

    private String generateSuggestion(double avgWaitTime, double avgSeatWaitTime, double lossRate, int maxSeatWaiting,
                                      int queueLostCount, int seatAbandonCount, String bottleneckType, String hotWindowSuggestion) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("当前主要瓶颈：" + bottleneckType + "。");

        if (lossRate > 0.15 || queueLostCount > 0 && avgWaitTime > 180) {
            suggestions.add("窗口排队承载能力不足，建议增加 2—3 个开放窗口，或提高热门窗口服务效率。");
        } else if (lossRate > 0.06 || avgWaitTime > 150) {
            suggestions.add("排队压力偏高，建议增加 1—2 个窗口，并优化窗口分流引导。");
        } else if (avgWaitTime > 80) {
            suggestions.add("排队时间略高，可在高峰时临时增开窗口。");
        }

        if (seatAbandonCount > 0 || avgSeatWaitTime > 120 || maxSeatWaiting > 90) {
            suggestions.add("座位瓶颈明显，建议增加座位、扩大等座区或引导错峰就餐。");
        } else if (avgSeatWaitTime > 60 || maxSeatWaiting > 40) {
            suggestions.add("端盘等座压力偏高，建议提高翻台效率并增加高峰期座位引导。");
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
