package com.luvin.ai.dto.story;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 1:1 장면(pair 블록)에서 내 빵의 대사에 남기는 피드백. 공통 데이터 계약 형식 그대로 order를 받지만 경로의 order가
 * 기준이고, 둘이 다르면 400이다.
 */
public record AiStoryFeedbackHttpRequest(
        Integer order,
        @NotBlank String blockId,
        @NotNull UUID messageId,
        @NotBlank @Size(max = 200) String text
) {
}
