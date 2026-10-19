package com.luvin.ai.dto;

import java.util.UUID;

public record AiMessageLikeView(
        UUID messageId,
        boolean liked
) {
}
