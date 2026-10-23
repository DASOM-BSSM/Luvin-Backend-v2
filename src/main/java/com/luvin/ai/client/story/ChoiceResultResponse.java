package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.Message;

import java.util.List;

/** 5-3 응답. fallbackBlocks는 거절일 때만 채워지고, Spring이 type pair 블록으로 바꾼다. */
public record ChoiceResultResponse(List<Message> messages, List<FallbackBlock> fallbackBlocks) {

    /** pairTarget: alone | friend */
    public record FallbackBlock(String id, String pairTarget, List<Message> messages) {
    }
}
