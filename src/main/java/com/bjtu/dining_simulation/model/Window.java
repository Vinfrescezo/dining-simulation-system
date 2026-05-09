package com.bjtu.dining_simulation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.LinkedList;
import java.util.Queue;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Window {
    private String id;                     // 窗口标识 ID
    private double speedFactor;            // 处理速度模型引用（或系数）
    private int maxQueueCapacity;          // 最大队列容量上限
    
    // 底层维护一个存放学生类对象的队列集合
    private Queue<Student> studentQueue = new LinkedList<>(); 
}