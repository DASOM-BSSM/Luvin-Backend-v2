package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.ChoiceRecordWithReason;
import com.luvin.ai.client.story.StoryTypes.EpisodeSummary;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;
import com.luvin.ai.client.story.StoryTypes.RelationRecord;
import com.luvin.ai.client.story.StoryTypes.ReportSeasonContext;

import java.util.List;

/** 5-7 POST /luvin/season-report. seasonContext는 2시즌부터 붙이고 1시즌은 null이다. */
public record SeasonReportRequest(GenerationContext context, String route, String romancePartnerId,
                                  List<EpisodeSummary> episodeSummaries, List<ChoiceRecordWithReason> choices,
                                  List<RelationRecord> relations, ReportSeasonContext seasonContext) {
}
