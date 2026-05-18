package com.bjtu.dining_simulation.controller;

// 1. 导入 Spring Boot 相关的注解
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

// 2. 导入我们三个 DTO 类
import com.bjtu.dining_simulation.dto.StartConfigDTO;
import com.bjtu.dining_simulation.dto.StartResponseDTO;
import com.bjtu.dining_simulation.dto.SimulationReportDTO;

// 3. 导入业务逻辑的 Service 类
import com.bjtu.dining_simulation.service.SimulationService;
import com.bjtu.dining_simulation.service.SimulationReportService;

@RestController          
@RequestMapping("/api/simulation")  
@CrossOrigin // 允许前端跨域请求            
public class SimulationController {

    @Autowired 
    private SimulationService simulationService; 

    @Autowired 
    private SimulationReportService reportService; 

    @PostMapping("/start")
    public StartResponseDTO startSimulation(@RequestBody StartConfigDTO config) {
        // 调用 SimulationService 处理启动逻辑
        System.out.println("\n" + "=".repeat(30));
        System.out.println("【后端接收：StartConfigDTO 参数对比日志】");
        System.out.println("1. 学生总数 (studentCount):    " + config.getStudentCount());
        System.out.println("2. 窗口数量 (windowCount):     " + config.getWindowCount());
        System.out.println("3. 仿真步数 (simDurationTick): " + config.getSimDurationTick());
        System.out.println("4. 座位总数 (seatCount):       " + config.getSeatCount());
        System.out.println("5. 排队上限 (maxQueueLength):  " + config.getMaxQueueLength());
        System.out.println("=".repeat(30) + "\n");
        return simulationService.startSimulation(config);
    }

    /*@GetMapping("/report")
    public SimulationReportDTO getSimulationReport() {
        // 调用专门的 ReportService 生成报表
        return reportService.generateReport(); 
    }*/
}