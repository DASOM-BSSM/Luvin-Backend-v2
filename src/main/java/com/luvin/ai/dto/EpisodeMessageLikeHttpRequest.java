package com.luvin.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EpisodeMessageLikeHttpRequest(
        @NotNull UUID messageId
) {
}
