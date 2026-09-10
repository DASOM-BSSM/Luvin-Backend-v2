package com.luvin.simulation.dto;

import jakarta.validation.constraints.NotBlank;

public record AiCloneCreateRequest(
        @NotBlank(message = "cloneName은 필수입니다.")
        String cloneName
) {
}