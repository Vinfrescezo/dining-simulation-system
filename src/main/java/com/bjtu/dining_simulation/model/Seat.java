package com.bjtu.dining_simulation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Seat {
    private String id;             // 座位唯一标识
    private boolean isOccupied;    // 占用标志位（true 为有人，false 为空位）
    private String studentId;      // 记录当前占用的学生 ID
    private int leaveTimer;        // 离场倒计时（就餐剩余时间）
}