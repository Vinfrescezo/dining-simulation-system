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

        int countToSpawn = calculatePoissonArrivalCount(ctx);
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
     * 泊松到达模型：每 Tick 独立抽样，到达人数服从 Poisson(λ)。
     *
     * λ 的取值采用"剩余人数 / 剩余时长"动态调整：
     *   λ_t = max(0, targetStudentCount - generated) / max(1, duration - tick)
     *
     * 这种动态 λ 的好处：
     *   1. 整个仿真期间到达率基本保持稳定（接近 N/T），符合"全天均匀就餐"语义；
     *   2. 当某段时间偶然生成偏少时，后续 λ 会略微回升，保证最终生成总数接近目标；
     *   3. 每个 Tick 是独立泊松抽样，到达人数随机波动，符合排队论经典模型。
     *
     * 与之前的截断正态分布相比，泊松到达不会在仿真后期"断流"，
     * 而是在整个时段内都有学生陆续进入。
     */
    private int calculatePoissonArrivalCount(SimulationService ctx) {
        int duration = Math.max(1, ctx.getSimDurationTick());
        int tick = Math.max(0, ctx.getGlobalTickCounter());
        int remainingTicks = Math.max(1, duration - tick);
        int remainingStudents = Math.max(0, ctx.getTargetStudentCount() - ctx.getGeneratedCount());
        if (remainingStudents <= 0) return 0;
        double lambda = (double) remainingStudents / remainingTicks;
        return samplePoisson(lambda);
    }

    /**
     * 泊松分布抽样。
     *   λ < 30：使用 Knuth 经典算法（数值稳定）
     *   λ ≥ 30：使用正态分布近似（均值 = λ，方差 = λ）
     */
    private int samplePoisson(double lambda) {
        if (lambda <= 0) return 0;
        if (lambda < 30) {
            double l = Math.exp(-lambda);
            int k = 0;
            double p = 1.0;
            do {
                k++;
                p *= random.nextDouble();
            } while (p > l);
            return k - 1;
        }
        double std = Math.sqrt(lambda);
        return Math.max(0, (int) Math.round(lambda + std * random.nextGaussian()));
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
