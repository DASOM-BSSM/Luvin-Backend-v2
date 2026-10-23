package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiCastRole;

import java.util.Map;

/** 수락·거절 판정과 호감 변화 수치. 기획 초기값이며 여기만 고치면 조정된다. */
public final class AiChoiceRules {

    /** 선택 지점(①~④)별 수락 기준 호감. */
    public static final Map<Integer, Integer> BASE_THRESHOLD = Map.of(1, 20, 2, 30, 3, 60, 4, 40);

    /** 불안형: 이전 선택에서 내 빵이 다른 빵을 고른 적이 있으면 기준이 올라간다. */
    public static final int ANXIOUS_OTHER_CHOSEN_PENALTY = 15;

    /** 회피형: 마음을 전하는 ③에서 기준이 올라간다. */
    public static final int AVOIDANT_CONFESSION_PENALTY = 10;
    public static final int CONFESSION_POINT = 3;

    /** 회피형은 ②에서 수락하되 대화를 짧게 생성한다. */
    public static final int AVOIDANT_SHORT_POINT = 2;

    /** 이 빵에게 이만큼 거절당했으면 무조건 수락한다. */
    public static final int MERCY_REJECT_COUNT = 2;

    /** 선택해서 수락되면 오르는 호감. 거절이면 변화 없음. */
    public static final int ACCEPTED_AFFINITY_BONUS = 8;

    /** 회차 완료 정리 단계가 제안하는 빵별 호감 변화량 허용 범위. */
    public static final int SUMMARY_DELTA_MIN = -5;
    public static final int SUMMARY_DELTA_MAX = 5;

    /** 시즌 시작 시 호감. 연애 후보 3명은 ①(기준 20)을 거의 수락하도록 그보다 조금 높게 둔다. */
    public static final Map<AiCastRole, Integer> INITIAL_AFFINITY = Map.of(
            AiCastRole.AVOIDANT, 25,
            AiCastRole.ANXIOUS, 25,
            AiCastRole.SECURE, 25,
            AiCastRole.FRIEND, 50,
            AiCastRole.MOOD_MAKER, 40);

    public static final String INITIAL_STATUS_TEXT = "처음 만남";
    public static final String ROMANCE_STATUS_TEXT = "연애 중";

    public static final String REASON_ALONE = "alone";
    public static final String REASON_AFFINITY_MET = "affinity_met";
    public static final String REASON_MERCY = "reject_mercy";
    public static final String REASON_AVOIDANT_SHORT = "avoidant_short";
    public static final String REASON_LOW_AFFINITY = "low_affinity";
    public static final String REASON_ANXIOUS_HURT = "anxious_hurt";
    public static final String REASON_AVOIDANT_RETREAT = "avoidant_retreat";

    private AiChoiceRules() {
    }

    public static int clampSummaryDelta(int delta) {
        return Math.max(SUMMARY_DELTA_MIN, Math.min(SUMMARY_DELTA_MAX, delta));
    }
}
