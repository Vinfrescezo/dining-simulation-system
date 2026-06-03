package com.bjtu.dining_simulation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.bjtu.dining_simulation.dto.StartConfigDTO;
import com.bjtu.dining_simulation.dto.StartResponseDTO;
import com.bjtu.dining_simulation.dto.SimulationReportDTO;
import com.bjtu.dining_simulation.service.SimulationService;
import com.bjtu.dining_simulation.service.SimulationReportService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin
public class SimulationController {

    @Autowired private SimulationService simulationService;
    @Autowired private SimulationReportService reportService;

    @PostMapping("/start")
    public StartResponseDTO startSimulation(@RequestBody StartConfigDTO config) {
        System.out.println("\n" + "=".repeat(30));
        System.out.println("【后端接收：StartConfigDTO 参数对比日志】");
        System.out.println("1. 学生总数 (studentCount):       " + config.getStudentCount());
        System.out.println("2. 窗口数量 (windowCount):        " + config.getWindowCount());
        System.out.println("3. 仿真步数 (simDurationTick):    " + config.getSimDurationTick());
        System.out.println("4. 座位总数 (seatCount):          " + config.getSeatCount());
        System.out.println("5. 排队上限 (maxQueueLength):     " + config.getMaxQueueLength());
        System.out.println("6. 等座区上限 (maxSeatWaitCapacity): " + config.getMaxSeatWaitCapacity());
        System.out.println("7. 等座耐心 (maxSeatWaitTick):    " + config.getMaxSeatWaitTick());
        System.out.println("=".repeat(30) + "\n");
        StartResponseDTO response = simulationService.startSimulation(config);
        response.setReport(reportService.generateAndSaveFinalReport());
        return response;
    }

    @GetMapping("/report")
    public SimulationReportDTO getSimulationReport() {
        return reportService.generateReport();
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getHistory(@RequestParam(defaultValue = "10") int limit) {
        return reportService.listRecentReports(limit);
    }
}
