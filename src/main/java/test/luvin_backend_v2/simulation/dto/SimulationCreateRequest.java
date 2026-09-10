package com.luvin.simulation.dto;

import jakarta.validation.constraints.NotNull;

public record SimulationCreateRequest(
        @NotNull(message = "cloneId는 필수입니다.")
        Long cloneId,
        String title
) {
}