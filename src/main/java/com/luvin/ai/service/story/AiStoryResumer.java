package com.luvin.ai.service.story;

import com.luvin.ai.config.AiStoryProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 서버가 재시작되면 끝나지 않은 회차 생성·완료를 이어서 진행한다(app.ai.story.resume-on-startup). */
@Component
public class AiStoryResumer {

    private final AiStoryService storyService;
    private final AiStoryProperties properties;

    public AiStoryResumer(AiStoryService storyService, AiStoryProperties properties) {
        this.storyService = storyService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeOnStartup() {
        if (properties.resumeOnStartup()) {
            storyService.resumeInterrupted();
        }
    }
}
