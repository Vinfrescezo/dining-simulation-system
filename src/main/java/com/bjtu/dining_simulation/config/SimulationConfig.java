package com.bjtu.dining_simulation.config;

import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class SimulationConfig {
    // 画布与核心坐标
    public final double CANVAS_WIDTH = 930.0;
    public final double CANVAS_HEIGHT = 620.0;
    
    // 入口与出口
    public final double DOOR_X = 58.0;
    public final double DOOR_Y = CANVAS_HEIGHT - 58.0;
    public final double EXIT_X = CANVAS_WIDTH - 58.0;
    public final double EXIT_Y = CANVAS_HEIGHT - 58.0;

    // 概率分布参数
    private final double orderingMu = 36.0;  
    private final double orderingSigma = 8.0;
    private final double eatingMu = 360.0;   
    private final double eatingSigma = 80.0;

    // 物理参数
    private final double moveSpeed = 12;    // 基础移动速度
    private int maxQueueLength = 20;
}