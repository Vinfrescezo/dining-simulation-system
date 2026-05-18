package com.bjtu.dining_simulation.service;

import com.bjtu.dining_simulation.config.SimulationConfig;
import com.bjtu.dining_simulation.dto.StartConfigDTO;
import com.bjtu.dining_simulation.dto.StartResponseDTO;
import com.bjtu.dining_simulation.engine.TrafficEngine;
import com.bjtu.dining_simulation.engine.WaitlistEngine; 
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
public class SimulationServiceTest {

    @InjectMocks
    private SimulationService simulationService;

    @Mock
    private SimulationConfig simulationConfig;

    @Mock
    private ResourceManager resourceManager;

    @Mock
    private TrafficEngine trafficEngine;

    @Mock
    private WaitlistEngine waitlistEngine;

    @Test
    void testStartSimulation_ShouldRunSuccessfully() {
        // --- 1. Arrange ---
        StartConfigDTO dto = new StartConfigDTO();

        // --- 2. Act ---
        StartResponseDTO response = simulationService.startSimulation(dto);

        // --- 3. Assert ---
        assertNotNull(response, "只要系统没崩溃，正常返回了结果，这个测试就算过！");
    }
}