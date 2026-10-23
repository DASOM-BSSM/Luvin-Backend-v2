package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.GenerationContext;
import com.luvin.ai.client.story.StoryTypes.RelationRecord;

import java.util.List;

/** 5-4 POST /luvin/interview. */
public record InterviewRequest(GenerationContext context, String route, String romancePartnerId, List<Item> items) {

    public record Item(String breadId, RelationRecord relation, List<ChoiceHistory> choiceHistory) {
    }

    public record ChoiceHistory(int pointId, boolean accepted, String reason) {
    }
}
