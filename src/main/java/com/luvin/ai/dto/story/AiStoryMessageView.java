package com.luvin.ai.dto.story;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * 블록 안 대사 한 줄. 기존 AiMessageView(InfernoChatMessage)와 같은 필드에, messenger 연출용 옵셔널 필드를 더했다.
 * read/typing/deletedAfterSend는 messenger 블록에서만 값이 있고 그 외에는 응답에서 빠진다.
 */
public record AiStoryMessageView(
        UUID messageId,
        Integer sequence,
        UUID speakerId,
        boolean fromRepresentative,
        String text,
        /** 상대가 읽었는지 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean read,
        /** 입력 중 표시 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean typing,
        /** 보냈다 지운 메시지 연출 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean deletedAfterSend
) {
}
