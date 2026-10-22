package com.luvin.ai.dto.story;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** breadId가 null이면 혼자 있기. */
public record AiChoiceHttpRequest(@NotNull Integer pointId, UUID breadId) {
}
