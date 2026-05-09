package com.bjtu.dining_simulation.controller;

import com.bjtu.dining_simulation.service.SimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController          // 告诉 Spring Boot 这是一个用来返回数据的 Controller (相当于 @Controller + @ResponseBody)
@RequestMapping("/api")  // 给所有接口加一个统一的前缀：http://localhost:8080/api/...
@CrossOrigin             // 允许跨域请求（非常重要！以后你写 Vue 或原生前端网页时，不用这个会报错）
public class SimulationControl {

    // 自动注入我们写好的“后厨”引擎
    @Autowired
    private SimulationService simulationService;

    /**
     * 接口：获取当前食堂的实时快照数据
     * 访问地址：http://localhost:8080/api/status
     */
    @GetMapping("/status")
    public Map<String, Object> getSimulationStatus() {
        // 创建一个 Map，用于组装返回给前端的数据
        Map<String, Object> responseData = new HashMap<>();

        // 1. 获取当前时间滴答
        responseData.put("currentTick", simulationService.getGlobalTickCounter());

        // 2. 获取当前在场的所有学生列表
        responseData.put("students", simulationService.getStudents());

        // 3. 获取所有窗口的排队情况
        responseData.put("windows", simulationService.getWindows());

        // 4. 获取座位占用情况
        responseData.put("seats", simulationService.getSeats());

        // Spring Boot 会自动把这个 Map 转换成前端能看懂的 JSON 格式
        return responseData;
    }
}