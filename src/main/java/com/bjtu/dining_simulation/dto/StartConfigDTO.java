package com.bjtu.dining_simulation.dto;
import org.springframework.stereotype.Component;
import lombok.Data;

@Component
@Data
public class StartConfigDTO {
    private int studentCount;
    private int windowCount;
    private int simDurationTick;
    private int seatCount;
    private int maxQueueLength;
}

