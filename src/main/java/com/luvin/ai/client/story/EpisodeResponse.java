package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.GeneratedBlock;
import com.luvin.ai.client.story.StoryTypes.Intro;

import java.util.List;

/** 5-2 응답. intro.title 8자 이내. */
public record EpisodeResponse(Intro intro, List<GeneratedBlock> blocks, List<String> threadsTouched) {
}
