package com.luvin.simulation.dto;

public record SimulationActionResponse(
        Long actionId,
        int roundNumber,
        String title,
        String detail
) {
}