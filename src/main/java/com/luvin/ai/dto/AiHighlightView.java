package com.luvin.ai.dto;

import java.util.UUID;

/** 5화 리포트의 하이라이트 메시지 하나. 실제 생성 대화에서 뽑힌 메시지의 ID와 원문이다. */
public record AiHighlightView(
        UUID messageId,
        String text
) {
}
