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
    
    // 完美对齐前端的数据结构
    private String orderingStudentId = null; 
    private int served = 0; 
    
    public Window(String id, double x, double y, Queue<Student> queue) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.studentQueue = queue;
    }
}