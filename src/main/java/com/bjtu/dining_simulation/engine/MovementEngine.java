package com.bjtu.dining_simulation.engine;

import com.bjtu.dining_simulation.model.Student;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class MovementEngine {
    private final Random random = new Random();

    /**
     * 计算步进位移
     * @return 是否到达目标点
     */
    public boolean moveTowards(Student s, double targetX, double targetY, double speed) {
        double dx = targetX - s.getX();
        double dy = targetY - s.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < speed) {
            s.setX(targetX);
            s.setY(targetY);
            return true;
        }
        s.setX(s.getX() + (dx / distance) * speed);
        s.setY(s.getY() + (dy / distance) * speed);
        return false;
    }

    public int calculateNormalTime(double mu, double sigma, int min) {
        return (int) Math.max(min, random.nextGaussian() * sigma + mu);
    }
}