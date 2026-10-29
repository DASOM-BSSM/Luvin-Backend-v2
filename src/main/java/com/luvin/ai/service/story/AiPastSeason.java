package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.dto.AiReportView;

import java.util.List;

/**
 * 새 시즌 설정 입력에 쓰는 지난 시즌 하나의 기록. 회차 흐름을 끝까지 진행하지 않은 시즌이면 route·report가 null일 수 있다.
 *
 * @param partner      연애 흐름의 최종 상대. 혼자 흐름이면 null.
 * @param castNames    내 빵을 뺀 출연진 이름.
 * @param stageSubtitle 1화 무대 소개 부제. 1화를 만들지 않았으면 null.
 * @param likedLines   하트를 누른 대사 원문(최근 순).
 * @param feedbacks    1:1 장면 피드백 글(최근 순).
 */
record AiPastSeason(int seasonNumber, AiSeasonSetting setting, AiStoryRoute route, Partner partner,
                    List<Choice> choices, AiReportView report, List<String> feedbacks, List<String> likedLines,
                    List<String> castNames, String stageSubtitle) {

    /** oneLine은 1화 무대 소개의 그 빵 소개(없으면 역할 이름). */
    record Partner(String name, AiCastRole castRole, String oneLine) {
    }

    /** breadName·castRole은 혼자 있기면 null. */
    record Choice(int pointId, int episodeOrder, String breadName, AiCastRole castRole, boolean accepted,
                  String reasonCode) {
    }
}
