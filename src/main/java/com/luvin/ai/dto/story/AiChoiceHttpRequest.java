package com.luvin.ai.dto.story;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** breadId가 null이면 혼자 있기. */
public record AiChoiceHttpRequest(
        @NotNull @Schema(minimum = "1", maximum = "4") Integer pointId,
        @Schema(types = {"string", "null"}, format = "uuid", description = "null이면 혼자 있기") UUID breadId
) {
}
