package com.bjtu.dining_simulation.model;

import lombok.AllArgsConstructor; // 必须导入这个
import lombok.Data;
import lombok.NoArgsConstructor;  // 必须导入这个

@Data
@AllArgsConstructor // <-- 加上这个，它才会支持 new Student("ID", 0.0, ...) 这种写法
@NoArgsConstructor  // <-- 加上这个，防止 MyBatis 或其他框架以后报错
public class Student {
    private String id;
    private double x;
    private double y;
    private String status;
    private String targetId;
    private int remainingTime;
}