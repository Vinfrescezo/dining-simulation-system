package com.bjtu.dining_simulation.config;

import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter // 自动生成所有字段的 get 方法
public class SimulationConfig {
    // 概率分布参数
    private final double orderingMu = 15.0;
    private final double orderingSigma = 5.0;
    private final double eatingMu = 1200.0;
    private final double eatingSigma = 300.0;

    // 物理参数
    private final double moveSpeed = 25.0;
    private final int studentSpawnRate = 3; 
    
    // 核心坐标
    public final double DOOR_X = 400.0;
    public final double DOOR_Y = 620.0;
    public final double WINDOW_Y = 120.0;
}