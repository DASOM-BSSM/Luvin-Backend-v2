package com.luvin.simulation.dto;

import com.luvin.simulation.SimulationStatus;

public record SimulationStatusResponse(
        Long simulationId,
        String title,
        SimulationStatus status,
        int actionCount,
        int interventionCount,
        String currentSummary
) {
}