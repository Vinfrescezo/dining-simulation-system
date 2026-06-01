package com.bjtu.dining_simulation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Queue;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Window {
    private String id;
    private double x;
    private double y;
    private Queue<Student> studentQueue;

    /**
     * 窗口展示名称与菜品信息。
     * popularityRank 越小表示越热门，popularityScore 越高表示越容易被学生偏好选择。
     */
    private String displayName;
    private String dishName;
    private int popularityRank = 99;
    private double popularityScore = 1.0;

    private String orderingStudentId = null;
    private int served = 0;
    private long totalQueueTime = 0;
    private int queueCompletedCount = 0;
    private int peakQueueLength = 0;

    // 窗口差异：preferenceWeight 越高越受欢迎，serviceWeight 越高代表服务略慢
    private double preferenceWeight = 0.0;
    private double serviceWeight = 0.0;

    public Window(String id, double x, double y, Queue<Student> queue) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.studentQueue = queue;
        this.displayName = id;
        this.dishName = id;
        this.totalQueueTime = 0;
        this.queueCompletedCount = 0;
        this.peakQueueLength = 0;
    }


    public void recordQueueTime(int queueTime) {
        this.totalQueueTime += Math.max(0, queueTime);
        this.queueCompletedCount++;
    }

    /**
     * 已经轮到打饭学生的平均排队时间。
     * 这个指标只统计“已经结束排队”的学生，适合做内部累计。
     */
    public double getAvgQueueTime() {
        return queueCompletedCount > 0 ? (double) totalQueueTime / queueCompletedCount : 0.0;
    }

    /**
     * 当前仍在窗口队伍中等待的人数。
     * 这里不把 ORDERING（正在打饭）的人算入队长，避免“服务中学生”把队长抬高。
     * PATHFINDING 学生已经选择了该窗口，仍会占用该窗口队列名额，因此计入等待队伍。
     */
    public int getWaitingQueueLength() {
        if (studentQueue == null) return 0;
        return (int) studentQueue.stream()
                .filter(s -> s != null && !"ORDERING".equals(s.getStatus()))
                .count();
    }

    /**
     * 当前仍在排队学生已经等待的总时间。
     * 用于阶段性报告，避免“队伍很长但平均排队时长很短”的反直觉情况。
     */
    public long getCurrentWaitingTimeSum(int currentTick) {
        if (studentQueue == null) return 0L;
        return studentQueue.stream()
                .filter(s -> s != null && !"ORDERING".equals(s.getStatus()))
                .filter(s -> s.getQueueStartTick() >= 0)
                .mapToLong(s -> Math.max(0, currentTick - s.getQueueStartTick()))
                .sum();
    }

    public int getCurrentWaitingStudentCount() {
        if (studentQueue == null) return 0;
        return (int) studentQueue.stream()
                .filter(s -> s != null && !"ORDERING".equals(s.getStatus()))
                .filter(s -> s.getQueueStartTick() >= 0)
                .count();
    }

    /**
     * 报告展示用平均排队时长：
     * 已经开始打饭学生的真实排队时间 + 当前仍在等待学生已经等了多久。
     * 这样可以更真实反映当前队伍压力。
     */
    public double getAvgQueueTimeIncludingCurrent(int currentTick) {
        long total = totalQueueTime + getCurrentWaitingTimeSum(currentTick);
        int count = queueCompletedCount + getCurrentWaitingStudentCount();
        return count > 0 ? (double) total / count : 0.0;
    }

    public void updatePeakQueueLength() {
        this.peakQueueLength = Math.max(this.peakQueueLength, getWaitingQueueLength());
    }
}
