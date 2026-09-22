package com.luvin.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record Episode1SelectionHttpRequest(
        @NotNull UUID partnerId
) {
}
