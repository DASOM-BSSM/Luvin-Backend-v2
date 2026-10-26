package com.luvin.learning.service;

import java.time.Duration;
import java.util.List;

/**
 * 학습 반영 규칙 상수. 팀이 테스트하며 조정하는 값은 전부 여기 모은다.
 *
 * <p>반영 시점: {@link LearningScheduler}가 {@link #BATCH_INTERVAL}마다 돈다(실시간 아님). 한 사용자에 대해
 * 아직 반영 안 된 신호가 MIN_SIGNALS개 이상 쌓였거나, 가장 오래된 신호가 {@link #MAX_WAIT}보다 오래됐으면 그 실행에서 반영한다.
 */
public final class LearningPolicy {

    private LearningPolicy() {
    }

    /** 학습 배치 주기(@Scheduled fixedDelay, ISO-8601). */
    public static final String BATCH_INTERVAL = "PT10M";
    /** 신호가 MIN_SIGNALS개 안 돼도, 가장 오래된 신호가 이만큼 기다렸으면 반영한다(= 하루 1번). */
    public static final Duration MAX_WAIT = Duration.ofHours(24);
    /** 한 번 실행에서 처리하는 사용자 수(학습 종류별). */
    public static final int USERS_PER_BATCH = 50;

    // ---------------------------------------------------------------- (A) 말투 학습

    /** 일기·댓글·그룹 답이 이만큼 쌓이면 말투 추출을 돌린다. */
    public static final int SPEECH_MIN_SIGNALS = 5;
    /** 한 번의 말투 추출에 넘기는 최근 글 수(N). */
    public static final int SPEECH_MAX_TEXTS = 20;
    /** 글 하나를 AI에 넘길 때 최대 글자 수(긴 일기는 앞부분만). */
    public static final int SPEECH_TEXT_MAX_CHARS = 1000;
    /**
     * 새로 추출한 말투가 섞이는 비율. 숫자 항목은 이전*(1-r) + 새 값*r, 목록 항목은 새 항목을 최대
     * {@link #SPEECH_LIST_NEW_ITEMS}개만 앞에 더한다.
     */
    public static final double SPEECH_BLEND_RATIO = 0.3;
    public static final int SPEECH_LIST_NEW_ITEMS = 2;
    public static final int SPEECH_LIST_MAX_ITEMS = 8;
    /** 말투 요약의 문자열 값 최대 길이. 넘으면 버린다(원문 문장이 통째로 들어오는 것을 막는다). */
    public static final int SPEECH_TEXT_VALUE_MAX = 120;
    public static final int SPEECH_LIST_VALUE_MAX = 30;
    /** 말투 요약의 문자열이 이 길이 이상으로 원문과 겹치면(인용) 버린다. */
    public static final int SPEECH_QUOTE_MIN_CHARS = 12;
    /** 저장하는 말투 요약 JSON 최대 크기(바이트 근사). 넘으면 이번 결과는 버린다. */
    public static final int SPEECH_JSON_MAX_CHARS = 4000;

    // ---------------------------------------------------------------- (B) 반죽 성격 학습

    /** 밸런스 답·그룹 답이 이만큼 쌓이면 성격 신호 추출을 돌린다. */
    public static final int PERSONALITY_MIN_SIGNALS = 3;
    public static final int PERSONALITY_MAX_ANSWERS = 20;
    /** 한 번 반영할 때 축별 변화량 상한(1~100 척도, 절댓값). */
    public static final double PERSONALITY_PER_RUN_CAP = 3.0;
    /** 설문 점수 대비 축별 누적 변화량 상한(절댓값). 설문 결과를 뒤집지 않도록 작게 둔다. */
    public static final double PERSONALITY_CUMULATIVE_CAP = 15.0;
    /** 누적 변화량이 이 이상인 축만 내 빵 성향 요약에 "최근 답변 경향"으로 적는다. */
    public static final double PERSONALITY_SUMMARY_MIN = 5.0;

    /** 성격 축 = 시즌 생성 입력(traits)의 13개 설문 차원 key. */
    public static final List<String> PERSONALITY_AXES = List.of(
            "affection_expression", "relationship_anxiety", "relationship_avoidance", "emotional_attunement",
            "relationship_initiative", "practical_priority", "reassurance_need", "jealousy_reactivity",
            "relationship_energy_dependence", "emotional_suppression", "conflict_confrontation",
            "relationship_pace", "interest_expression_frequency");
}
