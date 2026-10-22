package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 시즌 출연진 역할(시즌 공통 6명). 내 빵은 AI 시즌의 representative, 회피형/불안형/안정형은 AI가 뽑은
 * candidate 3명에 순서대로 배정하고, 친구와 분위기 메이커는 Spring이 출연진으로 추가한다.
 */
public enum AiCastRole {
    MY_BREAD(AiAttachmentStyle.NONE),
    AVOIDANT(AiAttachmentStyle.AVOIDANT),
    ANXIOUS(AiAttachmentStyle.ANXIOUS),
    SECURE(AiAttachmentStyle.SECURE),
    FRIEND(AiAttachmentStyle.NONE),
    MOOD_MAKER(AiAttachmentStyle.NONE);

    private final AiAttachmentStyle attachmentStyle;

    AiCastRole(AiAttachmentStyle attachmentStyle) {
        this.attachmentStyle = attachmentStyle;
    }

    public AiAttachmentStyle attachmentStyle() {
        return attachmentStyle;
    }

    /** ①~③ 선택 카드 후보가 되는 연애 후보(회피형/불안형/안정형)인지. */
    public boolean isRomanceCandidate() {
        return attachmentStyle != AiAttachmentStyle.NONE;
    }

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
