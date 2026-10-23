package com.luvin.ai.client.story;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.luvin.ai.client.story.StoryTypes.ChoiceRecord;
import com.luvin.ai.client.story.StoryTypes.EpisodeSummary;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;

import java.util.List;

/**
 * 5-6 POST /luvin/episode-note. choice는 이 회차의 선택(없으면 null).
 * episodeSummaries는 10화(시즌 종료 쪽지)에만 1~10화 요약을 담고, 그 외 회차에는 필드가 빠진다(AI 문서 P6).
 * 계약 5-6에 아직 없는 필드라 계약 문서에 추가가 필요하다.
 */
public record EpisodeNoteRequest(GenerationContext context, int order, String settingScene, String episodeSummary,
                                 ChoiceRecord choice, List<Relation> relations,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) List<EpisodeSummary> episodeSummaries) {

    public record Relation(String breadId, String statusText, String recentMemory) {
    }
}
