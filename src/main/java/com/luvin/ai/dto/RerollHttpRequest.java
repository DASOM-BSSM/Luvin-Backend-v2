package com.luvin.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RerollHttpRequest(
        @NotNull UUID partnerId
) {
}
