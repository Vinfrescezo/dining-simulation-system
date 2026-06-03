package com.bjtu.dining_simulation.engine;

import com.bjtu.dining_simulation.dto.SimulationEventDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SimulationEventLog {

    private final List<SimulationEventDTO> events = new ArrayList<>();

    public void reset() {
        events.clear();
    }

    public void record(int tick, String type, String studentId, String targetId, double x, double y) {
        events.add(new SimulationEventDTO(tick, type, studentId, targetId, x, y));
    }

    public List<SimulationEventDTO> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
