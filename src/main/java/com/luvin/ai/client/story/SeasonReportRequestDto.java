package com.luvin.ai.client.story;

import java.util.List;
import java.util.UUID;

/** 10화 완료 후 최종 리포트. 응답은 기존 리포트와 같은 형식(ReportResponseDto)이다. */
public record SeasonReportRequestDto(StoryContextDto context, String route, UUID romancePartnerId,
                                     List<EpisodeSummary> episodeSummaries) {

    public record EpisodeSummary(int order, String summary) {
    }
}
