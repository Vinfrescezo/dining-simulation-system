package com.bjtu.dining_simulation.controller;

import com.bjtu.dining_simulation.service.SimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController          
@RequestMapping("/api/simulation")  
@CrossOrigin             
public class SimulationControl {

    @Autowired
    private SimulationService simulationService;

    /**
     * 接收前端大屏的启动配置
     * 已经补全了 seatCount，与前端校验逻辑完全对齐
     */
    public static class StartConfigDTO {
        public int studentCount;
        public int windowCount;
        public int simDurationTick;
        public int seatCount; // 对应前端新加的座位控制
    }

    /**
     * 接口 1：启动/重置仿真
     * 对应前端：http.post('/api/simulation/start', config)
     */
    @PostMapping("/start")
    public Map<String, Object> startSimulation(@RequestBody StartConfigDTO config) {
        System.out.println(">>> 收到前端重置指令：窗口数=" + config.windowCount + 
                           ", 座位数=" + config.seatCount + ", 预计人数=" + config.studentCount);
        
        // 兜底默认值（防止前端传空）
        int seats = config.seatCount > 0 ? config.seatCount : 240;
        int windows = config.windowCount > 0 ? config.windowCount : 10;
        int duration = config.simDurationTick > 0 ? config.simDurationTick : 3600;
        
        // 调用我们刚刚重构的调度中心进行全局重置
        simulationService.resetSimulation(config.studentCount, windows, duration, seats);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "仿真已根据新参数重新启动");
        // 返回 SERVER_ 开头的 ID，前端 WebSocket 会带着这个 ID 连过来
        response.put("simId", "SERVER_" + System.currentTimeMillis()); 
        return response;
    }

    /**
     * 接口 2：生成并获取仿真最终报告
     * 对应前端：在仿真结束时调用此接口，获取数据用于图表展示和 CSV/JSON 下载
     */
    @GetMapping("/report")
    public Map<String, Object> getSimulationReport() {
        Map<String, Object> report = new HashMap<>();
        
        int arrived = simulationService.getGeneratedCount();
        int served = simulationService.getFinishedCount();
        int lost = simulationService.getLostCount();
        
        // 计算各项平均指标
        double avgWaitTime = served > 0 ? (double) simulationService.getTotalQueueTime() / served : 0;
        double avgSeatWaitTime = served > 0 ? (double) simulationService.getTotalSeatWaitTime() / served : 0;
        double lossRate = arrived > 0 ? (double) lost / arrived : 0;
        double seatTurnoverRate = !simulationService.getSeats().isEmpty() ? 
                (double) served / simulationService.getSeats().size() : 0;

        // 智能评分逻辑 (完全对齐前端 LocalDiningSimulator 的标准)
        String score = "运行顺畅";
        if (lossRate > 0.15 || avgWaitTime > 240) score = "极度拥挤";
        else if (lossRate > 0.06 || avgWaitTime > 150) score = "偏拥挤";
        else if (avgWaitTime > 80) score = "基本可控";

        // 智能优化建议
        int recommendedWindows = Math.max(0, (int) Math.ceil((avgWaitTime - 120) / 60.0));
        String suggestion = recommendedWindows > 0 
            ? "建议增加 " + recommendedWindows + " 个窗口，或将热门菜品拆分至快餐窗口以削峰。" 
            : "当前窗口配置基本可行，可重点观察座位周转与端盘等座区域。";

        // 组装最终 JSON 报告
        report.put("simId", "SERVER_" + System.currentTimeMillis());
        report.put("createdAt", new java.util.Date().toString());
        report.put("score", score);
        report.put("suggestion", suggestion);
        
        // 核心统计数据汇总
        Map<String, Object> summary = new HashMap<>();
        summary.put("avgWaitTime", Math.round(avgWaitTime));
        summary.put("avgSeatWaitTime", Math.round(avgSeatWaitTime));
        summary.put("seatTurnoverRate", Double.parseDouble(String.format("%.2f", seatTurnoverRate)));
        summary.put("lossRate", Double.parseDouble(String.format("%.2f", lossRate)));
        summary.put("maxCongestion", simulationService.getMaxCongestion());
        summary.put("generated", arrived);
        summary.put("finished", served);
        summary.put("lost", lost);
        summary.put("served", served);
        
        report.put("summary", summary);
        
        return report;
    }
}