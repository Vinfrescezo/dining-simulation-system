package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.dto.SimulationReportDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiReportService {
    @Value("${ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    public Map<String, Object> generateAnalysis(SimulationReportDTO report) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "deepseek");
        result.put("model", model);

        if (apiKey == null || apiKey.isBlank()) {
            result.put("available", false);
            result.put("content", "智能分析暂不可用：未配置 DEEPSEEK_API_KEY 环境变量。");
            return result;
        }
        if (report == null || report.getSummary() == null) {
            result.put("available", false);
            result.put("content", "智能分析暂不可用：缺少有效的仿真报告数据。");
            return result;
        }

        try {
            String endpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0.2);
            body.put("max_tokens", 1600);
            body.put("stream", false);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是一名高校食堂运营分析助手，擅长根据仿真统计数据给出简洁、客观、可执行的分析。"),
                    Map.of("role", "user", "content", buildPrompt(report))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                result.put("available", false);
                result.put("content", "智能分析生成失败：DeepSeek 接口返回 HTTP " + response.statusCode() + "。请检查 API Key、模型名或账户额度。");
                return result;
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                content = "智能分析生成失败：模型未返回有效文本。";
            }
            result.put("available", true);
            result.put("content", content);
            return result;
        } catch (Exception e) {
            result.put("available", false);
            result.put("content", "智能分析暂不可用：" + e.getMessage());
            return result;
        }
    }

    private String buildPrompt(SimulationReportDTO report) {
        SimulationReportDTO.Summary s = report.getSummary();
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下北京交通大学食堂仿真结果，生成一份中文智能分析报告。\n");
        sb.append("要求：\n");
        sb.append("1. 不要编造未提供的数据；\n");
        sb.append("2. 必须按\"总体判断：\"\"主要问题：\"\"优化建议：\"三个小节输出；\n");
        sb.append("3. 每个小节输出 2—3 条，每条单独换行，使用\"1.\"\"2.\"编号；\n");
        sb.append("4. 每条内容要具体一些，尽量引用已提供的指标或窗口名称；\n");
        sb.append("5. 给出的建议要面向食堂管理者，强调可执行措施；\n");
        sb.append("6. 不要输出 Markdown 标题符号、代码块或表格。\n\n");
        sb.append("【基础结论】\n");
        sb.append("综合得分：").append(report.getNumericScore()).append("分\n");
        sb.append("五级评级：").append(report.getGradeLevel()).append('\n');
        sb.append("主要扣分项：").append(report.getDeductionReason()).append('\n');
        sb.append("主要瓶颈：").append(report.getBottleneckType()).append('\n');
        sb.append("瓶颈原因：").append(report.getBottleneckReason()).append('\n');
        sb.append("系统建议：").append(report.getSuggestion()).append("\n\n");
        sb.append("【核心指标】\n");
        sb.append("生成学生：").append(s.getGenerated()).append("人\n");
        sb.append("完成就餐：").append(s.getFinished()).append("人\n");
        sb.append("成功打饭：").append(s.getServed()).append("人\n");
        sb.append("窗口流失：").append(s.getQueueLost()).append("人\n");
        sb.append("平均排队时长：").append(s.getAvgWaitTime()).append("秒\n");
        sb.append("平均找座时长：").append(s.getAvgSeatWaitTime()).append("秒\n");
        sb.append("平均用餐时长：").append(s.getAvgEatingTime()).append("秒\n");
        sb.append("座位周转率：").append(s.getSeatTurnoverRate()).append("次/座\n");
        sb.append("窗口流失率：").append(String.format("%.2f%%", s.getLossRate() * 100)).append('\n');
        sb.append("最大在场人数：").append(s.getMaxCongestion()).append("人\n");
        sb.append("最大等座人数：").append(s.getMaxSeatWaiting()).append("人\n\n");
        sb.append("【窗口数据】\n");
        if (report.getWindowPerformance() != null) {
            report.getWindowPerformance().stream()
                    .sorted((a, b) -> Double.compare(b.getAvgQueueLength(), a.getAvgQueueLength()))
                    .limit(8)
                    .forEach(w -> sb.append(w.getId()).append(' ')
                            .append(w.getDishName()).append("：平均队长")
                            .append(w.getAvgQueueLength()).append("，平均排队")
                            .append(w.getAvgWaitTime()).append("秒，服务")
                            .append(w.getTotalServedCount()).append("人\n"));
        }
        return sb.toString();
    }
}
