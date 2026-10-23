package com.luvin.ai.dto.story;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** 1:1 장면(pair 블록) 메시지 좋아요. liked로 켜고 끈다(토글이 아니라 원하는 상태를 보낸다). */
public record AiStoryLikeHttpRequest(
        Integer order,
        @NotBlank String blockId,
        @NotNull UUID messageId,
        @NotNull Boolean liked
) {
}
