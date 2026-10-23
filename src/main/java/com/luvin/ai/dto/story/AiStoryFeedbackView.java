package com.luvin.ai.dto.story;

import java.util.UUID;

public record AiStoryFeedbackView(int order, String blockId, UUID messageId, String text) {
}
