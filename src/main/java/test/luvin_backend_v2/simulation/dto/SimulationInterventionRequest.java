package com.luvin.simulation.dto;

import jakarta.validation.constraints.NotBlank;

public record SimulationInterventionRequest(
        @NotBlank(message = "message는 필수입니다.")
        String message
) {
}