package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.ChoiceRecord;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;

import java.util.List;

/** 5-6 POST /luvin/episode-note. choice는 이 회차의 선택(없으면 null). */
public record EpisodeNoteRequest(GenerationContext context, int order, String settingScene, String episodeSummary,
                                 ChoiceRecord choice, List<Relation> relations) {

    public record Relation(String breadId, String statusText, String recentMemory) {
    }
}
