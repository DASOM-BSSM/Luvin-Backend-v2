package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSeasonSetting;

import java.util.random.RandomGenerator;

/**
 * 사용자 나이로 시즌 배경, audience(minor/adult), 출연 빵 연령대를 정한다. 구간 경계는 상수로 둔다.
 *
 * 19세 이하는 학교 배경이고, 같은 구간을 minor로 본다 — 입력받는 나이가 만 나이인지 아직 확정되지 않아,
 * 학교 배경 사용자에게 성인 대상 생성이 나가지 않도록 보수적으로 잡았다.
 */
public final class AiSeasonSettingPolicy {

    public static final int SCHOOL_MAX_AGE = 19;
    public static final int YOUNG_ADULT_MAX_AGE = 23;
    public static final int MINOR_MAX_AGE = 19;

    public static final String AUDIENCE_MINOR = "minor";
    public static final String AUDIENCE_ADULT = "adult";

    private AiSeasonSettingPolicy() {
    }

    /** 20~23세는 대학·아르바이트 중 하나를 뽑는다. 호출부가 결과를 시즌에 저장해 시즌 동안 고정한다. */
    public static AiSeasonSetting decideSetting(int age, RandomGenerator random) {
        if (age <= SCHOOL_MAX_AGE) {
            return AiSeasonSetting.SCHOOL;
        }
        if (age <= YOUNG_ADULT_MAX_AGE) {
            return random.nextBoolean() ? AiSeasonSetting.CAMPUS : AiSeasonSetting.CAFE;
        }
        return AiSeasonSetting.OFFICE;
    }

    public static String audience(int age) {
        return age <= MINOR_MAX_AGE ? AUDIENCE_MINOR : AUDIENCE_ADULT;
    }

    /** 출연 빵 나이대를 사용자와 비슷하게 맞추라고 AI에 넘기는 연령대. */
    public static String ageBand(int age) {
        if (age <= SCHOOL_MAX_AGE) {
            return "teen";
        }
        if (age <= YOUNG_ADULT_MAX_AGE) {
            return "early_twenties";
        }
        return "mid_twenties_or_older";
    }
}
