package com.luvin.ai.client.story;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;
import com.luvin.ai.client.story.StoryTypes.RelationRecord;

/** 5-3 POST /luvin/choice-result. 혼자 있기(breadId null)는 호출하지 않는다. reason은 거절일 때만. */
public record ChoiceResultRequest(
        GenerationContext context,
        int order,
        int pointId,
        String breadId,
        boolean accepted,
        String reason,
        @JsonProperty("short") boolean shortReply,
        RelationRecord relation
) {
}
