package com.luvin.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AiEpisodeFeedbackView(
        UUID messageId,
        String content,
        LocalDateTime createdAt
) {
}
