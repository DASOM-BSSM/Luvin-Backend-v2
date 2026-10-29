package com.luvin.ai.client.story;

import com.luvin.ai.client.dto.TraitsDto;

import java.util.List;

/**
 * POST /luvin/season-design 응답. NPC 5명(candidate 3, friend 1, host 1)과 1~10화 뼈대 초안이다. breadId·성별·애착 유형은
 * 없다 — Spring이 정한다. traits·designIntent·testsWeakness는 내부용이라 앱에 보내지 않는다.
 */
public record SeasonDesignResponse(Stage stage, List<Cast> cast, List<Episode> episodes) {

    public static final String CANDIDATE = "candidate";
    public static final String FRIEND = "friend";
    public static final String HOST = "host";

    /** title은 배경별 고정(In School 등), subtitle은 한 줄 30자. */
    public record Stage(String title, String subtitle) {
    }

    /** name 한글 2~3자, role 12자, description 20자. */
    public record Cast(String castRole, String name, String breadType, String role, String description,
                       TraitsDto traits, String designIntent) {
    }

    /** choicePoint: 3화=1, 5화=2, 6화=3, 8화=4, 나머지 null. testsWeakness는 이전 리포트 약점 원문 또는 null. */
    public record Episode(int order, String title, String situation, String transitionText, Integer choicePoint,
                          String testsWeakness) {
    }
}
