package com.luvin.ai.dto;

import java.util.UUID;

public record AiMessageView(
        UUID messageId,
        Integer sequence,
        AiSceneKind sceneKind,
        UUID speakerId,
        boolean fromRepresentative,
        String text
) {
}
