package com.luvin.user.service;

import com.luvin.user.domain.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 감정일기에서 사용자 대신 보여주는 빵 이름("쫀쫀한 카스테라", "반죽" 없음). 앱과 같은 규칙이다 — 앱 설문 결과 화면
 * (Luvin-Frontend-v2 map-bread-result.ts의 deriveAdjective)이 최신 설문 결과 id로 형용사를 고르고, 빵 종류 이름을
 * 붙인다. 형용사는 설문 결과 id 문자열의 32비트 해시(앱의 hashString = Java String.hashCode와 같은 계산)의 절댓값을
 * 형용사 수로 나눈 나머지로 고른다. 그래서 재설문으로 새 결과가 생겨야만 이름이 바뀐다.
 *
 * 형용사 목록과 순서는 앱(character-persona.ts의 ADJECTIVES)과 같아야 한다. 바꾸면 앱도 함께 바꾼다.
 */
public final class BreadNames {

    static final List<String> ADJECTIVES = List.of(
            "차가운", "따뜻한", "똑똑한", "조용한", "다정한", "진지한", "성숙한", "유쾌한", "귀여운", "도도한",
            "발랄한", "느긋한", "꼼꼼한", "섬세한", "솔직한", "엉뚱한", "털털한", "세련된", "쫀쫀한");

    /** 빵 타입 id(설문 profile id) → 빵 종류 이름(설문 설정의 label과 같다). */
    static final Map<String, String> BREAD_LABELS = Map.of(
            "cream_bread", "크림빵", "red_bean_bread", "팥빵", "salt_bread", "소금빵", "pretzel", "프레첼",
            "donut", "도넛", "baguette", "바게트", "madeleine", "마들렌", "castella", "카스테라");

    private BreadNames() {
    }

    /** 최신 설문 결과 id와 빵 타입 id로 빵 이름을 만든다. 설문 전(둘 중 하나라도 없음)이거나 모르는 빵 타입이면 null. */
    public static String of(UUID latestSurveyResultId, String breadType) {
        String label = breadType == null ? null : BREAD_LABELS.get(breadType);
        if (latestSurveyResultId == null || label == null) {
            return null;
        }
        return adjective(latestSurveyResultId.toString()) + " " + label;
    }

    /** 사용자의 빵 이름. 사용자가 없으면(탈퇴) null. */
    public static String of(User user) {
        return user == null ? null : of(user.getLatestSurveyResultId(), user.getPersonalityType());
    }

    static String adjective(String seed) {
        long hash = Math.abs((long) seed.hashCode());
        return ADJECTIVES.get((int) (hash % ADJECTIVES.size()));
    }
}
