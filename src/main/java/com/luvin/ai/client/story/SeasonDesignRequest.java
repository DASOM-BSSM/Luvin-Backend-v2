package com.luvin.ai.client.story;

import com.luvin.ai.client.dto.TraitsDto;

import java.util.List;

/**
 * POST /luvin/season-design(새 시즌 설정, task season-design). 2시즌부터 1화 생성 때 무대 소개(5-1) 대신 부른다.
 * 모든 필드가 필수이고 null 허용 필드(finalPartner)도 키는 보내야 한다. 이름·빵 종류·부제는 이전 시즌과 겹치지 않게
 * Spring이 비교 대상 전체를 보낸다.
 */
public record SeasonDesignRequest(
        /** male | female. */
        String gender,
        int age,
        /** 내 빵의 13개 성향(0~100). 이 시즌 생성 입력의 값이다. */
        TraitsDto traits,
        int seasonNumber,
        String setting,
        /** 최근 순 최대 3개. 첫 항목은 seasonNumber - 1이다. */
        List<PreviousSeason> previousSeasons,
        /** NPC가 고를 수 있는 빵 종류(내 빵 종류 제외, 서로 다른 5개 이상). */
        List<String> allowedBreadTypes,
        List<String> previousCastNames,
        List<String> previousStageSubtitles
) {

    /** route: romance | solo. solo면 finalPartner는 null이다. */
    public record PreviousSeason(int seasonNumber, String setting, String route, PreviousPartner finalPartner,
                                 List<PreviousChoice> choices, PreviousReport report, List<String> userFeedbacks) {
    }

    /** castRole: candidate | friend | host. */
    public record PreviousPartner(String name, String castRole, String oneLine) {
    }

    /** point와 episode는 1=3화, 2=5화, 3=6화, 4=8화. 고른 상대·수락 여부·사유는 summary 한 줄에 담는다. */
    public record PreviousChoice(int point, int episode, String summary) {
    }

    public record PreviousReport(String tendency, String feedback, List<String> strengths, List<String> weaknesses) {
    }
}
