package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSeasonSetting;

import java.util.List;
import java.util.Map;

/**
 * 장면 블록 회차(1~10화)의 고정 구성표. 회차 구성은 AI가 아니라 이 표와 {@link AiEpisodePlanner}의 코드 규칙으로 정한다.
 * 문구는 기획 문서의 표를 그대로 옮겼다 — 바꿀 때는 이 파일만 고치면 된다.
 */
public final class AiStoryCatalog {

    public static final int FIRST_ORDER = 1;
    public static final int FINAL_ORDER = 10;

    /** 회차별 공통 사건(배경과 무관). index = order - 1. */
    private static final List<String> COMMON_EVENTS = List.of(
            "무리에 처음 합류",
            "단톡방이 생김",
            "처음 둘만 남음",
            "다 같이 큰 행사 준비",
            "나를 대하는 방식이 갈림",
            "행사 당일, 마음을 전함",
            "바빠서 거리가 생김",
            "갈등",
            "화해의 기회",
            "시즌 마무리 대화");

    /** 배경별 장면. index = order - 1. */
    private static final Map<AiSeasonSetting, List<String>> SETTING_SCENES = Map.of(
            AiSeasonSetting.SCHOOL, List.of(
                    "새 학기 반 배정", "반 단톡", "청소 당번", "축제 준비", "아파서 조퇴한 날",
                    "축제 당일", "시험 기간", "반 행사 의견 충돌", "체육대회", "학기 말"),
            AiSeasonSetting.CAMPUS, List.of(
                    "동아리 신입 환영회", "조별과제 단톡", "과제하다 남은 둘", "MT 준비", "시험 전날",
                    "MT 밤", "과제 폭탄", "무임승차", "종강 모임", "종강"),
            AiSeasonSetting.OFFICE, List.of(
                    "입사 동기 첫 모임", "동기 단톡", "야근 후 퇴근길", "워크숍 준비", "업무 실수한 날",
                    "워크숍 저녁", "마감 시즌", "업무 분담 갈등", "프로젝트 마무리", "연말"),
            AiSeasonSetting.CAFE, List.of(
                    "첫 출근", "근무 단톡", "마감 같이 하는 날", "시즌 행사 준비", "진상 손님 응대 후",
                    "행사 주말", "근무 시간 엇갈림", "대타 갈등", "마감 후 야식", "마지막 근무 날"));

    /** 선택 지점(①~④)이 있는 회차. */
    private static final Map<Integer, Integer> POINT_BY_ORDER = Map.of(3, 1, 5, 2, 6, 3, 8, 4);

    /** 선택 카드 질문. "혼자 있기"는 프론트가 항상 붙이므로 여기에 넣지 않는다. */
    private static final Map<Integer, String> QUESTION_BY_POINT = Map.of(
            1, "둘만 남은 지금, 누구에게 먼저 말을 걸어볼까?",
            2, "오늘 누구한테 연락해볼까?",
            3, "오늘 밤, 누구에게 마음을 전해볼까?",
            4, "누구와 먼저 이야기해볼까?");

    /**
     * 기존 본편 5화를 참고 줄거리로 넣는 회차와 그 줄거리 텍스트(FastAPI 입력 referencePlot).
     * 줄거리 텍스트를 기획/AI 쪽에서 받기 전이라 비어 있다 — 받으면 회차별로 채운다(비어 있으면 null을 보낸다).
     */
    private static final Map<Integer, String> REFERENCE_PLOTS = Map.of(1, "", 2, "", 3, "", 6, "", 10, "");

    /**
     * 출연진 이름의 형용사("형용사 + 빵 종류"). 성향이 드러나지 않는 중립적인 말만 쓴다(차가운·예민한 같은 말 금지).
     * 서비스의 기존 형용사 목록을 받으면 교체한다.
     */
    public static final List<String> NEUTRAL_ADJECTIVES = List.of(
            "느긋한", "포근한", "산뜻한", "말랑한", "고소한", "반짝이는", "동그란", "보송한",
            "새콤한", "따끈한", "바삭한", "몽글한");

    /** 그룹 메신저 방 이름(배경별). */
    private static final Map<AiSeasonSetting, String> GROUP_ROOM_NAMES = Map.of(
            AiSeasonSetting.SCHOOL, "2학년 2반 단톡방ㅎㅎ",
            AiSeasonSetting.CAMPUS, "새내기 과 동기방",
            AiSeasonSetting.OFFICE, "입사 동기방",
            AiSeasonSetting.CAFE, "매장 근무자 단톡");

    private AiStoryCatalog() {
    }

    public static boolean isValidOrder(int order) {
        return order >= FIRST_ORDER && order <= FINAL_ORDER;
    }

    public static String commonEvent(int order) {
        return COMMON_EVENTS.get(order - 1);
    }

    public static String settingScene(AiSeasonSetting setting, int order) {
        return SETTING_SCENES.get(setting).get(order - 1);
    }

    /** 그 회차 끝에 선택 지점이 없으면 null. */
    public static Integer pointOf(int order) {
        return POINT_BY_ORDER.get(order);
    }

    public static int orderOf(int pointId) {
        return POINT_BY_ORDER.entrySet().stream()
                .filter(e -> e.getValue() == pointId)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 선택 지점: " + pointId));
    }

    public static String question(int pointId) {
        return QUESTION_BY_POINT.get(pointId);
    }

    /** 참고 줄거리 텍스트. 참고 회차가 아니거나 아직 텍스트가 없으면 null. */
    public static String referencePlot(int order) {
        String plot = REFERENCE_PLOTS.get(order);
        return plot == null || plot.isBlank() ? null : plot;
    }

    /** 1~3화는 열기 없이 무료로 볼 수 있다. 4화부터는 처음 열 때 토큰을 쓴다. */
    public static final int LAST_FREE_ORDER = 3;

    public static boolean isFree(int order) {
        return order <= LAST_FREE_ORDER;
    }

    /** 사용자 성별(MALE/FEMALE, 대소문자 무관)의 반대. 알 수 없는 값이면 null. 결과는 대문자 형식이다. */
    public static String oppositeGender(String gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender.trim().toUpperCase()) {
            case "MALE" -> "FEMALE";
            case "FEMALE" -> "MALE";
            default -> null;
        };
    }

    public static String groupRoomName(AiSeasonSetting setting) {
        return GROUP_ROOM_NAMES.get(setting);
    }
}
