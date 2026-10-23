package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.BlockDesign;
import com.luvin.ai.client.story.StoryTypes.ChoiceRecord;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;
import com.luvin.ai.client.story.StoryTypes.RelationRecord;
import com.luvin.ai.client.story.StoryTypes.Thread;

import java.util.List;

/** 5-2 POST /luvin/episode. 10화는 design에 interview를 넣지 않고 5-4로 따로 생성한다. */
public record EpisodeRequest(
        GenerationContext context,
        int order,
        String commonEvent,
        String settingScene,
        String route,
        String romancePartnerId,
        /** 기존 본편 참고 줄거리(1·2·3·6·10화). 줄거리 텍스트를 받기 전에는 null. */
        String referencePlot,
        List<BlockDesign> design,
        List<Thread> threads,
        List<RelationRecord> relations,
        List<ChoiceRecord> choices,
        List<String> usedMaterials
) {
}
