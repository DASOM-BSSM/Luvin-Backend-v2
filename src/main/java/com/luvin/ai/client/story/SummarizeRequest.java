package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.GenerationContext;
import com.luvin.ai.client.story.StoryTypes.RelationRecord;
import com.luvin.ai.client.story.StoryTypes.SceneBlock;

import java.util.List;

/** 5-5 POST /luvin/summarize. blocks는 최종 재생된 블록(choice_result 포함). */
public record SummarizeRequest(GenerationContext context, int order, List<SceneBlock> blocks,
                               List<RelationRecord> relations) {
}
