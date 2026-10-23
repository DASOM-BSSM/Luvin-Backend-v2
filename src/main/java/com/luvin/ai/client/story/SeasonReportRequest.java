package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.ChoiceRecordWithReason;
import com.luvin.ai.client.story.StoryTypes.EpisodeSummary;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;
import com.luvin.ai.client.story.StoryTypes.RelationRecord;

import java.util.List;

/** 5-7 POST /luvin/season-report. */
public record SeasonReportRequest(GenerationContext context, String route, String romancePartnerId,
                                  List<EpisodeSummary> episodeSummaries, List<ChoiceRecordWithReason> choices,
                                  List<RelationRecord> relations) {
}
