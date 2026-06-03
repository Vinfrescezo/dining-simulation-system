package com.bjtu.dining_simulation.engine;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.model.Student;
import com.bjtu.dining_simulation.model.Window;
import com.bjtu.dining_simulation.service.ResourceManager;
import com.bjtu.dining_simulation.service.SimulationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

@Component
public class TrafficEngine {

    @Autowired private SimulationConfig config;
    @Autowired private ResourceManager resourceManager;
    @Autowired private SimulationEventLog eventLog;

    private final Random random = new Random();

    public void reset() {
        // 当前到达模型采用“目标累计人数 - 已生成人数”的方式，不需要额外累加器。
    }

    public void processSpawning(SimulationService ctx) {
        int remaining = ctx.getTargetStudentCount() - ctx.getGeneratedCount();
        if (remaining <= 0) return;

        int countToSpawn = calculateNormalArrivalCount(ctx);
        countToSpawn = Math.min(remaining, countToSpawn);
        if (countToSpawn <= 0) return;

        for (int i = 0; i < countToSpawn; i++) {
            ctx.addGeneratedCount();

            String preferredWindowId = pickPreferredWindowId(ctx);
            Window targetWindow = resourceManager.getBestQueueWindow(
                    config.getDOOR_X(), config.getDOOR_Y(), config.getMaxQueueLength(), preferredWindowId);

            if (targetWindow == null) {
                ctx.addQueueLostCount();
                eventLog.record(ctx.getGlobalTickCounter(), "LOST", "lost-" + ctx.getGeneratedCount(), null, 0, 0);
                continue;
            }

            String sId = "学生-" + UUID.randomUUID().toString().substring(0, 4);
            Student s = new Student(sId, config.getDOOR_X(), config.getDOOR_Y(), "PATHFINDING", targetWindow.getId(), 0);
            s.setPreferredWindowId(preferredWindowId);
            s.setChosenWindowId(targetWindow.getId());
            s.setTargetX(targetWindow.getX());
            s.setTargetY(targetWindow.getY() + 60);
            s.setQueueStartTick(ctx.getGlobalTickCounter());

            ctx.getStudents().add(s);
            targetWindow.getStudentQueue().add(s);
            targetWindow.updatePeakQueueLength();
            eventLog.record(ctx.getGlobalTickCounter(), "ARRIVE", sId, targetWindow.getId(), config.getDOOR_X(), config.getDOOR_Y());
        }
    }

    /**
     * 按“截断正态分布的累计分布函数”决定当前 Tick 应累计生成多少学生。
     *
     * 为什么不用“每 Tick 概率 + 末尾补齐”？
     * 旧逻辑在仿真末尾为了补齐人数，会突然生成一批学生，导致到达人数曲线尾部出现异常尖峰。
     * 这里改为累计目标人数：
     *   当前应生成人数 = 总人数 × CDF(t)
     *   本 Tick 生成人数 = 当前应生成人数 - 已生成人数
     *
     * 这样可以同时保证：
     * 1. 总人数最终等于 studentCount；
     * 2. 到达曲线整体呈单峰正态分布；
     * 3. 不会在最后几个 Tick 暴力补齐，避免假高峰。
     */
    private int calculateNormalArrivalCount(SimulationService ctx) {
        int duration = Math.max(1, ctx.getSimDurationTick());
        int tick = Math.max(0, Math.min(ctx.getGlobalTickCounter(), duration));

        double progress = (double) tick / duration;
        double fraction = truncatedNormalCdf(progress, config.getArrivalPeakCenter(), config.getArrivalPeakSigma());

        int expectedGenerated;
        if (tick >= duration) {
            expectedGenerated = ctx.getTargetStudentCount();
        } else {
            expectedGenerated = (int) Math.floor(ctx.getTargetStudentCount() * fraction);
        }

        return Math.max(0, expectedGenerated - ctx.getGeneratedCount());
    }

    /**
     * 将正态分布限制在 [0, 1] 的仿真进度区间内，并重新归一化。
     * 返回值表示从仿真开始到 progress 为止，理论上应该到达的累计比例。
     */
    private double truncatedNormalCdf(double progress, double mu, double sigma) {
        double safeSigma = Math.max(0.0001, sigma);
        double start = normalCdf((0.0 - mu) / safeSigma);
        double end = normalCdf((1.0 - mu) / safeSigma);
        double current = normalCdf((progress - mu) / safeSigma);
        double denominator = Math.max(0.0001, end - start);
        double value = (current - start) / denominator;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    /**
     * Abramowitz and Stegun 公式 7.1.26 的 erf 近似，精度足够用于仿真到达分布。
     */
    private double erf(double x) {
        double sign = x < 0 ? -1.0 : 1.0;
        x = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return sign * y;
    }

    private String pickPreferredWindowId(SimulationService ctx) {
        if (ctx.getWindows().isEmpty()) return null;
        // 约 55% 的学生会有菜品偏好，偏好概率按窗口热度加权。
        // 热门窗口会更容易被选择，但最终仍会被队伍长度、距离和服务速度共同制衡。
        if (random.nextDouble() > 0.55) return null;
        double totalWeight = 0.0;
        for (Window w : ctx.getWindows()) {
            totalWeight += Math.max(0.1, w.getPopularityScore());
        }
        double ticket = random.nextDouble() * totalWeight;
        double acc = 0.0;
        for (Window w : ctx.getWindows()) {
            acc += Math.max(0.1, w.getPopularityScore());
            if (ticket <= acc) return w.getId();
        }
        return ctx.getWindows().get(0).getId();
    }
}
