package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.Thread;

import java.util.List;

/** 5-5 응답. affinityDelta(-5~+5)는 Spring이 범위를 검사해 반영한다. choiceMemory 20자 이내. */
public record SummarizeResponse(String episodeSummary, List<RelationUpdate> relations) {

    public record RelationUpdate(String breadId, String memorySummary, String recentMemory, List<String> newFacts,
                                 List<Thread> threadsAdd, List<String> threadsResolved, String statusText,
                                 Integer affinityDelta, String affinityReason, String choiceMemory,
                                 /** formal | casual | null(변경 없음). Spring이 배경 규칙에 맞을 때만 반영한다. */
                                 String speechLevel) {
    }
}
