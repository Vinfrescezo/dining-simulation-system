package com.bjtu.dining_simulation.repository;

import com.bjtu.dining_simulation.dto.SimulationReportDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class SimulationHistoryRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void saveReport(SimulationReportDTO report) {
        if (report == null || report.getSummary() == null) return;
        ensureExtraColumns();
        SimulationReportDTO.Summary s = report.getSummary();
        jdbcTemplate.update("""
            INSERT INTO sim_report_history
            (sim_id, created_at, score, bottleneck_type, bottleneck_reason, suggestion, hot_window_suggestion,
             avg_wait_time, avg_seat_wait_time, avg_eating_time, seat_turnover_rate, loss_rate,
             max_congestion, max_seat_waiting, generated, finished, lost, queue_lost, seat_abandoned, served)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            report.getSimId(), report.getCreatedAt(), report.getScore(), report.getBottleneckType(), report.getBottleneckReason(),
            report.getSuggestion(), report.getTopHotWindowSuggestion(), s.getAvgWaitTime(), s.getAvgSeatWaitTime(),
            s.getAvgEatingTime(), s.getSeatTurnoverRate(), s.getLossRate(), s.getMaxCongestion(), s.getMaxSeatWaiting(),
            s.getGenerated(), s.getFinished(), s.getLost(), s.getQueueLost(), s.getSeatAbandoned(), s.getServed()
        );
    }

    public List<Map<String, Object>> listRecentReports(int limit) {
        ensureExtraColumns();
        return jdbcTemplate.queryForList("""
            SELECT sim_id, created_at, score, bottleneck_type, suggestion, avg_wait_time, avg_seat_wait_time,
                   loss_rate, max_congestion, max_seat_waiting, generated, finished, lost, served
            FROM sim_report_history
            ORDER BY id DESC
            LIMIT ?
            """, Math.max(1, Math.min(limit, 50)));
    }

    private void ensureExtraColumns() {
        addColumnIfMissing("bottleneck_type", "VARCHAR(64)");
        addColumnIfMissing("bottleneck_reason", "VARCHAR(512)");
        addColumnIfMissing("hot_window_suggestion", "VARCHAR(512)");
    }

    private void addColumnIfMissing(String column, String type) {
        try {
            jdbcTemplate.execute("ALTER TABLE sim_report_history ADD COLUMN " + column + " " + type);
        } catch (Exception ignored) {
            // Column already exists or table will be created by schema.sql on first run.
        }
    }
}
