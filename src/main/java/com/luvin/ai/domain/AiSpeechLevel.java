package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 출연 빵이 내 빵에게 쓰는 말투(공통 데이터 계약 7장 "배경별 말투"). 시즌 시작 값은 school이면 casual(반말),
 * 나머지 배경은 formal(존댓말)이다. 정리 단계(P4)가 바꾸자고 해도 배경 규칙에 맞을 때만 바꾸고,
 * casual → formal로 되돌아가지는 않는다.
 */
public enum AiSpeechLevel {
    FORMAL,
    CASUAL;

    public static AiSpeechLevel initial(AiSeasonSetting setting) {
        return setting == AiSeasonSetting.SCHOOL ? CASUAL : FORMAL;
    }

    /**
     * 정리 단계가 준 말투 변경을 반영할지. campus는 formal → casual 허용, office·cafe는 연애 흐름의 연인만 casual 허용.
     * 같은 값이거나 casual → formal은 반영하지 않는다(false).
     */
    public static boolean allowsChange(AiSeasonSetting setting, AiSpeechLevel current, AiSpeechLevel requested,
                                       boolean romancePartner) {
        if (requested != CASUAL || current == CASUAL) {
            return false;
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
