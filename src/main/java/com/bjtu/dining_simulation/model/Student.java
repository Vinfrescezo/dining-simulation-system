package com.bjtu.dining_simulation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Student {
    private String id;
    private double x;
    private double y;
    private String status;   // 状态：PATHFINDING, QUEUEING, ORDERING, WAITING_FOR_SEAT, SEEK_SEAT, EATING, LEAVING
    private String targetId; // 目标窗口或座位ID

    // --- 寻路/移动目标坐标 ---
    private double targetX;
    private double targetY;

    // --- 模拟时间与剩余时间 ---
    private int remainingTime; // 点餐或吃饭的剩余tick

    // ==========================================
    // ?? 新增字段：用于统计分析与报告生成
    // ==========================================
    private int queueStartTick;     // 开始排队的时间
    private Integer seatWaitStartTick; // 开始等座的时间 (Integer允许为null)
    
    private int totalWaitTime = 0;     // 累计排队等餐时间
    private int totalSeatWaitTime = 0; // 累计端盘等座时间

    // 默认构造方法外的全参构造，方便初始化
    public Student(String id, double x, double y, String status, String targetId, int remainingTime) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.status = status;
        this.targetId = targetId;
        this.remainingTime = remainingTime;
    }
}