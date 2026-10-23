package com.luvin.ai.client.story;

import java.util.List;

/**
 * 5-7 응답(부록 B: 기존 최종 리포트 응답 형식). 부록 B가 AI 쪽에서 채워지기 전이라, 기존 리포트 응답을
 * camelCase로 옮기고 최종 상대를 빵 id로 받는다(솔로면 null일 수 있음).
 */
public record SeasonReportResponse(String finalPartnerId, String tendency, String feedback, List<String> strengths,
                                   List<String> weaknesses, Highlights highlights, String renderMode) {

    public record Highlights(Highlight episode2, Highlight episode4) {
    }

    public record Highlight(String message, String reason) {
    }
}
