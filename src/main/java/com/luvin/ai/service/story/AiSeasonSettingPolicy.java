package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSeasonSetting;

import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 사용자 나이로 시즌 배경, audience(minor/adult), 출연 빵 연령대를 정한다. 구간 경계는 상수로 둔다.
 *
 * 19세 이하는 학교 배경이고, 같은 구간을 minor로 본다 — 입력받는 나이가 만 나이인지 아직 확정되지 않아,
 * 학교 배경 사용자에게 성인 대상 생성이 나가지 않도록 보수적으로 잡았다.
 *
 * 두 번째 시즌부터는 나이에 맞는 배경 중 지난 시즌들에서 가장 오래 안 쓴 배경을 고른다({@link #decideNextSetting}).
 */
public final class AiSeasonSettingPolicy {

    public static final int SCHOOL_MAX_AGE = 19;
    public static final int YOUNG_ADULT_MAX_AGE = 23;
    public static final int MINOR_MAX_AGE = 19;
    public static final int CAMPUS_MAX_AGE = 29;

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

    /**
     * 나이에 맞는 배경(새 시즌에서 고를 수 있는 배경). 10대는 학교·아르바이트, 20~29세는 대학·아르바이트·회사,
     * 30세 이상은 회사·아르바이트다.
     */
    public static List<AiSeasonSetting> allowedSettings(int age) {
        if (age <= SCHOOL_MAX_AGE) {
            return List.of(AiSeasonSetting.SCHOOL, AiSeasonSetting.CAFE);
        }
        if (age <= CAMPUS_MAX_AGE) {
            return List.of(AiSeasonSetting.CAMPUS, AiSeasonSetting.CAFE, AiSeasonSetting.OFFICE);
        }
        return List.of(AiSeasonSetting.OFFICE, AiSeasonSetting.CAFE);
    }

    /**
     * 새 시즌 배경. 지난 시즌이 없으면(첫 시즌, 시즌 초기화 뒤) {@link #decideSetting}처럼 나이로 정한다. 있으면 나이에
     * 맞는 배경 중 안 쓴 배경을, 다 썼으면 가장 오래전에 쓴 배경을 고른다(같은 조건이면 무작위). 그래서 배경이 2개
     * 이상인 나이에서는 직전 시즌과 겹치지 않는다.
     *
     * @param previous 지난 시즌 배경, 최근 시즌부터. 배경이 없는(시작 전) 시즌은 null일 수 있다.
     */
    public static AiSeasonSetting decideNextSetting(int age, List<AiSeasonSetting> previous, RandomGenerator random) {
        List<AiSeasonSetting> used = previous.stream().filter(s -> s != null).toList();
        if (used.isEmpty()) {
            return decideSetting(age, random);
        }
        List<AiSeasonSetting> allowed = allowedSettings(age);
        // 최근 시즌일수록 작은 값, 안 쓴 배경은 가장 큰 값 — 가장 큰 값을 가진 배경들 중에서 고른다.
        Comparator<AiSeasonSetting> staleness = Comparator.comparingInt(s -> used.contains(s) ? used.indexOf(s)
                : Integer.MAX_VALUE);
        AiSeasonSetting stalest = allowed.stream().max(staleness).orElseThrow();
        List<AiSeasonSetting> candidates = allowed.stream()
                .filter(s -> staleness.compare(s, stalest) == 0)
                .toList();
        return candidates.get(random.nextInt(candidates.size()));
    }

    public static String audience(int age) {
        return age <= MINOR_MAX_AGE ? AUDIENCE_MINOR : AUDIENCE_ADULT;
    }

    /** 출연 빵 나이대를 사용자와 비슷하게 맞추라고 AI에 넘기는 연령대(계약 GenerationContext.ageGroup). */
    public static String ageGroup(int age) {
        if (age <= 16) {
            return "10대 중반";
        }
        if (age <= SCHOOL_MAX_AGE) {
            return "10대 후반";
        }
        if (age <= YOUNG_ADULT_MAX_AGE) {
            return "20대 초반";
        }
        if (age <= 29) {
            return "20대 중후반";
        }
        return "30대 이상";
    }
}
