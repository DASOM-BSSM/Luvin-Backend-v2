package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.Message;

import java.util.List;

/** 5-4 응답. */
public record InterviewResponse(List<Interview> interviews) {

    public record Interview(String breadId, List<Message> messages) {
    }
}
