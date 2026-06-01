package com.bjtu.dining_simulation.config;

import com.bjtu.dining_simulation.service.SimulationReportService;
import com.bjtu.dining_simulation.service.SimulationService;
import com.fasterxml.jackson.databind.JsonNode;
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

    @Autowired private SimulationService simulationService;
    @Autowired private SimulationReportService reportService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this, "/ws/simulation").setAllowedOriginPatterns("*");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        sendToSession(session, buildInitMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText("");
            if ("PAUSE".equalsIgnoreCase(type)) {
                simulationService.setPaused(true);
                sendAck(session, "PAUSE", true);
            } else if ("RESUME".equalsIgnoreCase(type) || "START".equalsIgnoreCase(type)) {
                simulationService.setPaused(false);
                sendAck(session, "RESUME", true);
            } else if ("SPEED".equalsIgnoreCase(type)) {
                double factor = node.path("factor").asDouble(1.0);
                simulationService.setSpeedFactor(factor);
                sendAck(session, "SPEED", true);
            } else if ("GENERATE_REPORT".equalsIgnoreCase(type)) {
                // 用户在监控页点击“生成当前报告”时触发。
                // 为了保证报告和画面处在同一个时间点，这里先暂停仿真，再返回阶段性报告。
                simulationService.setPaused(true);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "REPORT");
                payload.put("simId", simulationService.getCurrentSimId());
                payload.put("tick", simulationService.getGlobalTickCounter());
                payload.put("report", reportService.generateReport());
                sendToSession(session, payload);
            }
        } catch (Exception e) {
            sendError(session, "WebSocket控制消息格式不正确");
        }
    }

    @Scheduled(fixedRate = 100)
    public void broadcastStatus() {
        if (sessions.isEmpty()) return;
        try {
            Map<String, Object> snapshot = buildSnapshot();
            if (simulationService.isFinished() && simulationService.markFinishReported()) {
                snapshot.put("type", "FINISHED");
                snapshot.put("done", true);
                snapshot.put("report", reportService.generateAndSaveFinalReport());
            }
            String jsonStr = objectMapper.writeValueAsString(snapshot);
            TextMessage message = new TextMessage(jsonStr);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) session.sendMessage(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, Object> buildInitMessage() {
        Map<String, Object> init = new HashMap<>();
        init.put("type", "INIT");
        init.put("simId", simulationService.getCurrentSimId());
        init.put("seats", buildSeatList());
        init.put("windows", buildWindowList());
        return init;
    }

    private Map<String, Object> buildSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("type", "SNAPSHOT");
        snapshot.put("simId", simulationService.getCurrentSimId());
        snapshot.put("tick", simulationService.getGlobalTickCounter());
        snapshot.put("generated", simulationService.getGeneratedCount());
        snapshot.put("finished", simulationService.getFinishedCount());
        snapshot.put("lost", simulationService.getLostCount());
        snapshot.put("queueLost", simulationService.getQueueLostCount());
        snapshot.put("seatAbandoned", simulationService.getSeatAbandonCount());

        List<Map<String, Object>> studentList = simulationService.getStudents().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("x", Math.round(s.getX()));
            m.put("y", Math.round(s.getY()));
            m.put("s", s.getStatus());
            return m;
        }).collect(Collectors.toList());
        snapshot.put("students", studentList);
        snapshot.put("windows", buildWindowList());

        snapshot.put("seats", buildSeatList());
        long occupiedSeats = simulationService.getSeats().stream().filter(seat -> seat.isOccupied()).count();
        long reservedSeats = simulationService.getSeats().stream().filter(seat -> seat.isReserved()).count();
        long waitingSeatCount = simulationService.getStudents().stream().filter(st -> "WAITING_FOR_SEAT".equals(st.getStatus())).count();
        long searchingSeatCount = simulationService.getStudents().stream().filter(st -> "SEEK_SEAT".equals(st.getStatus()) && st.getReservedSeatId() == null).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", studentList.size());
        stats.put("occupiedSeats", occupiedSeats);
        stats.put("reservedSeats", reservedSeats);
        stats.put("waitingSeatCount", waitingSeatCount);
        stats.put("searchingSeatCount", searchingSeatCount);
        stats.put("avgWaitTime", Math.round(simulationService.getRealtimeAvgQueueTime()));
        stats.put("avgSeatWaitTime", Math.round(simulationService.getRealtimeAvgSeatWaitTime()));
        stats.put("avgEatingTime", Math.round(simulationService.getRealtimeAvgEatingTime()));
        stats.put("lossRate", Math.round(simulationService.getLossRate() * 10000.0) / 10000.0);
        stats.put("maxCongestion", simulationService.getMaxCongestion());
        stats.put("maxSeatWaiting", simulationService.getMaxSeatWaiting());
        stats.put("generated", simulationService.getGeneratedCount());
        stats.put("finished", simulationService.getFinishedCount());
        stats.put("lost", simulationService.getLostCount());
        stats.put("queueLost", simulationService.getQueueLostCount());
        stats.put("seatAbandoned", simulationService.getSeatAbandonCount());
        stats.put("speedFactor", simulationService.getSpeedFactor());
        stats.put("paused", simulationService.isPaused());
        stats.putAll(reportService.generateRealtimeDiagnosis());
        double progress = simulationService.getSimDurationTick() == 0 ? 0 : (simulationService.getGlobalTickCounter() * 100.0) / simulationService.getSimDurationTick();
        stats.put("progress", Math.min(100, Math.round(progress)));
        snapshot.put("stats", stats);
        return snapshot;
    }

    private List<Map<String, Object>> buildWindowList() {
        return simulationService.getWindows().stream().map(w -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", w.getId());
            m.put("name", w.getDisplayName());
            m.put("dishName", w.getDishName());
            m.put("popularityRank", w.getPopularityRank());
            m.put("popularityScore", Math.round(w.getPopularityScore() * 100.0) / 100.0);
            m.put("qLen", w.getWaitingQueueLength());
            m.put("served", w.getServed());
            m.put("avgWaitTime", Math.round(w.getAvgQueueTimeIncludingCurrent(simulationService.getGlobalTickCounter())));
            m.put("peakQueueLength", w.getPeakQueueLength());
            m.put("preferenceWeight", Math.round(w.getPreferenceWeight() * 100.0) / 100.0);
            m.put("serviceWeight", Math.round(w.getServiceWeight() * 100.0) / 100.0);
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildSeatList() {
        return simulationService.getSeats().stream().map(seat -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", seat.getId());
            m.put("x", Math.round(seat.getX()));
            m.put("y", Math.round(seat.getY()));
            m.put("occupied", seat.isOccupied());
            m.put("reserved", seat.isReserved());
            m.put("occupancyTicks", seat.getOccupancyTicks());
            return m;
        }).collect(Collectors.toList());
    }

    private void sendAck(WebSocketSession session, String command, boolean ok) {
        Map<String, Object> ack = new HashMap<>();
        ack.put("type", "CONTROL_ACK");
        ack.put("command", command);
        ack.put("ok", ok);
        ack.put("speedFactor", simulationService.getSpeedFactor());
        sendToSession(session, ack);
    }

    private void sendError(WebSocketSession session, String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("type", "ERROR");
        err.put("message", msg);
        sendToSession(session, err);
    }

    private void sendToSession(WebSocketSession session, Map<String, Object> data) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
            }
        } catch (Exception ignored) {}
    }
}
