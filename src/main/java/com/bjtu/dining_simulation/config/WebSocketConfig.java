package com.bjtu.dining_simulation.config;

import com.bjtu.dining_simulation.service.SimulationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocket
public class WebSocketConfig extends TextWebSocketHandler implements WebSocketConfigurer {

    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    
    @Autowired
    private SimulationService simulationService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 允许跨域连接 WebSocket
        registry.addHandler(this, "/ws/simulation").setAllowedOrigins("*");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Scheduled(fixedRate = 100) // 100ms 发送频率，保证前端动画丝滑
    public void broadcastStatus() {
        if (sessions.isEmpty()) return;
        
        try {
            Map<String, Object> snapshot = new HashMap<>();
            
            // --- 根节点数据 (保留在此处防崩，兼容旧版前端) ---
            snapshot.put("tick", simulationService.getGlobalTickCounter());
            snapshot.put("generated", simulationService.getGeneratedCount());
            snapshot.put("finished", simulationService.getFinishedCount());
            snapshot.put("lost", simulationService.getLostCount());

            // --- 核心对齐逻辑 1：学生状态映射 s ---
            List<Map<String, Object>> studentList = simulationService.getStudents().stream().map(s -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", s.getId());
                m.put("x", Math.round(s.getX() * 10.0) / 10.0);
                m.put("y", Math.round(s.getY() * 10.0) / 10.0);
                m.put("s", s.getStatus()); // 前端 LocalDiningSimulator 需要的是 's' 字段
                return m;
            }).collect(Collectors.toList());
            snapshot.put("students", studentList);

            // --- 核心对齐逻辑 2：窗口队列长度 qLen ---
            List<Map<String, Object>> windowList = simulationService.getWindows().stream().map(w -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", w.getId());
                m.put("qLen", w.getStudentQueue().size()); // 前端需要 qLen 来显示数字
                m.put("served", w.getServed());
                return m;
            }).collect(Collectors.toList());
            snapshot.put("windows", windowList);

            // --- 核心对齐逻辑 3：座位占用状态 occupied ---
            List<Map<String, Object>> seatList = simulationService.getSeats().stream().map(seat -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", seat.getId());
                m.put("x", seat.getX());
                m.put("y", seat.getY());
                m.put("occupied", seat.isOccupied()); // 前端变色靠这个字段
                return m;
            }).collect(Collectors.toList());
            snapshot.put("seats", seatList);

            // --- 4. 统计面板 (核心修复区) ---
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeCount", studentList.size());
            stats.put("occupiedSeats", seatList.stream().filter(st -> (boolean)st.get("occupied")).count());
            stats.put("waitingSeatCount", studentList.stream().filter(st -> "WAITING_FOR_SEAT".equals(st.get("s"))).count());
            stats.put("maxCongestion", simulationService.getMaxCongestion());
            
            stats.put("generated", simulationService.getGeneratedCount());
            stats.put("finished", simulationService.getFinishedCount());
            stats.put("lost", simulationService.getLostCount());

            // 计算进度百分比
            double progress = simulationService.getSimDurationTick() == 0 ? 0 : 
                             (simulationService.getGlobalTickCounter() * 100.0) / simulationService.getSimDurationTick();
            stats.put("progress", Math.min(100, Math.round(progress)));
            
            // 将 stats 挂载到最终数据结构上
            snapshot.put("stats", stats);

            // 转化为 JSON 字符串并向所有连接的客户端广播
            String jsonStr = objectMapper.writeValueAsString(snapshot);
            TextMessage message = new TextMessage(jsonStr);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) session.sendMessage(message);
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}