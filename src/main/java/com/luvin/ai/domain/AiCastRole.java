package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 시즌 출연진 역할(시즌 공통 6명). 내 빵은 AI 시즌의 representative, 회피형/불안형/안정형은 AI가 뽑은
 * candidate 3명에 순서대로 배정하고, 친구와 분위기 메이커는 Spring이 출연진으로 추가한다.
 */
public enum AiCastRole {
    MY_BREAD(AiAttachmentStyle.NONE, "me", "내 빵"),
    AVOIDANT(AiAttachmentStyle.AVOIDANT, "avoidant", "중심 후보"),
    ANXIOUS(AiAttachmentStyle.ANXIOUS, "anxious", "중심 후보"),
    SECURE(AiAttachmentStyle.SECURE, "secure", "중심 후보"),
    FRIEND(AiAttachmentStyle.NONE, "friend", "내 빵의 친구"),
    MOOD_MAKER(AiAttachmentStyle.NONE, "maker", "분위기 메이커");

    private final AiAttachmentStyle attachmentStyle;
    private final String contractId;
    private final String seasonRole;

    AiCastRole(AiAttachmentStyle attachmentStyle, String contractId, String seasonRole) {
        this.attachmentStyle = attachmentStyle;
        this.contractId = contractId;
        this.seasonRole = seasonRole;
    }

    /** 공통 데이터 계약의 BreadId(역할 고정 id). FastAPI와는 캐릭터 UUID 대신 이 값으로 주고받는다. */
    public String contractId() {
        return contractId;
    }

    /** 공통 데이터 계약 7장의 역할 이름(무대 소개 생성 입력). */
    public String seasonRole() {
        return seasonRole;
    }

    public static java.util.Optional<AiCastRole> fromContractId(String contractId) {
        return java.util.Arrays.stream(values()).filter(r -> r.contractId.equals(contractId)).findFirst();
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
