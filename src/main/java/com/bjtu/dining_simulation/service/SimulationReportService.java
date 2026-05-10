package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.dto.SimulationReportDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class SimulationReportService {

    // 注入核心模拟服务，仅仅是为了读取它的统计数据
    @Autowired 
    private SimulationService simService; 

    /**
     * 生成最终的仿真报告
     */
    public SimulationReportDTO generateReport() {
        SimulationReportDTO report = new SimulationReportDTO();
        
        // 读取核心引擎的数据
        int finishedCount = simService.getFinishedCount();
        int generatedCount = simService.getGeneratedCount();
        int lostCount = simService.getLostCount();
        int totalSeats = simService.getSeats().size();
        
        // 1. 计算各项平均指标
        double avgWaitTime = finishedCount > 0 ? (double) simService.getTotalQueueTime() / finishedCount : 0;
        double avgSeatWaitTime = finishedCount > 0 ? (double) simService.getTotalSeatWaitTime() / finishedCount : 0;
        double lossRate = generatedCount > 0 ? (double) lostCount / generatedCount : 0;
        double seatTurnoverRate = totalSeats > 0 ? (double) finishedCount / totalSeats : 0;

        // 2. 智能评分与建议 (逻辑全部封装在这里)
        report.setScore(calculateScore(lossRate, avgWaitTime));
        report.setSuggestion(generateSuggestion(avgWaitTime));

        // 3. 组装基础数据...
        report.setSimId("SERVER_" + System.currentTimeMillis());
        report.setCreatedAt(new Date().toString());
        
        SimulationReportDTO.Summary summary = new SimulationReportDTO.Summary();
        summary.setAvgWaitTime(Math.round(avgWaitTime));
        summary.setAvgSeatWaitTime(Math.round(avgSeatWaitTime));
        summary.setSeatTurnoverRate(Math.round(seatTurnoverRate * 100.0) / 100.0);
        summary.setLossRate(Math.round(lossRate * 100.0) / 100.0);
        summary.setMaxCongestion(simService.getMaxCongestion());
        summary.setGenerated(generatedCount);
        summary.setFinished(finishedCount);
        summary.setLost(lostCount);
        report.setSummary(summary);
        
        return report;
    }

    // 内部私有方法：把复杂的 if-else 拆解开，让代码更易读
    private String calculateScore(double lossRate, double avgWaitTime) {
        if (lossRate > 0.15 || avgWaitTime > 240) return "极度拥挤";
        if (lossRate > 0.06 || avgWaitTime > 150) return "偏拥挤";
        if (avgWaitTime > 80) return "基本可控";
        return "运行顺畅";
    }

    private String generateSuggestion(double avgWaitTime) {
        int recommendedWindows = Math.max(0, (int) Math.ceil((avgWaitTime - 120) / 60.0));
        if (recommendedWindows > 0) {
            return "建议增加 " + recommendedWindows + " 个窗口，或将热门菜品拆分至快餐窗口以削峰。";
        }
        return "当前窗口配置基本可行，可重点观察座位周转与端盘等座区域。";
    }
}