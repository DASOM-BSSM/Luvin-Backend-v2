package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 출연 빵이 내 빵에게 쓰는 말투(공통 데이터 계약 7장 "배경별 말투", reconstruction.v22 역할별 말투). 시즌 시작 값은
 * 친구면 배경과 상관없이 casual(반말), 그 밖의 역할은 school이면 casual, 나머지 배경은 formal(존댓말)이다.
 * 정리 단계(P4)가 바꾸자고 해도 배경·역할 규칙에 맞을 때만 바꾸고, casual → formal로 되돌아가지는 않는다.
 */
public enum AiSpeechLevel {
    FORMAL,
    CASUAL;

    /** 친구는 원래 친한 사이라 어느 배경이든 casual이다. castRole을 모르면(출연진 배정 전) 배경으로만 정한다. */
    public static AiSpeechLevel initial(AiSeasonSetting setting, AiCastRole castRole) {
        if (castRole == AiCastRole.FRIEND) {
            return CASUAL;
        }
        return setting == AiSeasonSetting.SCHOOL ? CASUAL : FORMAL;
    }

    /**
     * 저장된 관계 말투를 읽을 때의 현재 말투. 비어 있으면 시즌 시작 값이고, 친구는 저장 값이 formal이어도(v22 이전에
     * 만든 관계) casual로 본다 — 교정 마이그레이션(V18) 뒤에 옛 코드가 만든 관계가 formal을 다시 AI에 넘기지 않게 한다.
     */
    public static AiSpeechLevel current(AiSpeechLevel stored, AiSeasonSetting setting, AiCastRole castRole) {
        if (castRole == AiCastRole.FRIEND) {
            return CASUAL;
        }
        return stored != null ? stored : initial(setting, castRole);
    }

    /**
     * 정리 단계가 준 말투 변경을 반영할지. 친구는 어느 배경이든 casual 허용, 그 밖에는 campus는 formal → casual 허용,
     * office·cafe는 연애 흐름의 연인만 casual 허용. 같은 값이거나 casual → formal은 반영하지 않는다(false).
     */
    public static boolean allowsChange(AiSeasonSetting setting, AiCastRole castRole, AiSpeechLevel current,
                                       AiSpeechLevel requested, boolean romancePartner) {
        if (requested != CASUAL || current == CASUAL) {
            return false;
        }
        if (castRole == AiCastRole.FRIEND) {
            return true;
        }
        return switch (setting) {
            case SCHOOL, CAMPUS -> true;
            case OFFICE, CAFE -> romancePartner;
        };
    }

    /** formal | casual. 모르는 값이면 null. */
    public static AiSpeechLevel fromWire(String value) {
        if (value == null) {
            return null;
        }
        for (AiSpeechLevel level : values()) {
            if (level.wireValue().equals(value)) {
                return level;
            }
        }
        return null;
    }

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
