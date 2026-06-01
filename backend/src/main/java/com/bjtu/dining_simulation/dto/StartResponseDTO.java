package com.bjtu.dining_simulation.dto;
import lombok.Data;

@Data
public class StartResponseDTO {
    private int code;
    private String status;
    private String msg;
    private String message;
    private String simId;
    private Object data;
}
