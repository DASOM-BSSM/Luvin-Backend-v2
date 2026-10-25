package com.luvin.ai.dto.story;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * 블록 안 대사 한 줄. 기존 AiMessageView 필드에 공통 데이터 계약의 kind와 messenger 연출용 옵셔널 필드를 더했다.
 * kind가 narration이면 내레이션이라 speakerId가 null이다. read/typing/deletedAfterSend는 messenger 블록에서만 온다.
 */
public record AiStoryMessageView(
        UUID messageId,
        Integer sequence,
        /** 말한 빵(participant의 breadId). 내레이션이면 null */
        UUID speakerId,
        /** chat | monologue | narration */
        @Schema(allowableValues = {"chat", "monologue", "narration"}) String kind,
        boolean fromRepresentative,
        String text,
        /** 상대가 읽었는지 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean read,
        /** 입력 중 표시 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean typing,
        /** 보냈다 지운 메시지 연출 */
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean deletedAfterSend,
        /** messenger 전용: 이야기 속 보낸 시각(예: "오후 4시 50분"). 없으면 앱이 임시 시각을 만든다. */
        @JsonInclude(JsonInclude.Include.NON_NULL) String sentAtLabel
) {
}
