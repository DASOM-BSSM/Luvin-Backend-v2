package com.luvin.simulation.dto;

public record SimulationReportResponse(
        Long simulationId,
        String reportSummary
) {
}