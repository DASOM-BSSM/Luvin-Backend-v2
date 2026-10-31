package com.luvin.ai.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEpisodeContentTest {

    @Test
    void 앱이_기다리던_생성의_실패는_한_번_알린다() {
        AiEpisodeContent content = new AiEpisodeContent(1L, null, 2, null);
        content.markFailed("STORY_GENERATION_FAILED");
        assertTrue(content.reportFailureOnce());
        assertFalse(content.reportFailureOnce());
    }

    @Test
    void 미리_생성의_실패는_알리지_않아_다음_요청이_바로_다시_만든다() {
        AiEpisodeContent content = new AiEpisodeContent(1L, null, 2, null);
        content.markFailedUnseen("STORY_GENERATION_FAILED");
        assertEquals(AiStoryContentStatus.FAILED, content.getStatus());
        assertFalse(content.reportFailureOnce());
    }
}
