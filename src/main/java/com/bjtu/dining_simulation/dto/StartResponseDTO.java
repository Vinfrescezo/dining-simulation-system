package com.bjtu.dining_simulation.dto;
import lombok.Data;

@Data
public class StartResponseDTO {
    private String status;
    private String message;
    private String simId;
}