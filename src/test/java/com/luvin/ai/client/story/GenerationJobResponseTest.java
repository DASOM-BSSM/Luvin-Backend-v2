package com.luvin.ai.client.story;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationJobResponseTest {

    @Test
    void 재생성_코드는_진행_상태이고_끝난_것으로_보지_않는다() {
        GenerationJobResponse job = job("regenerating", null, "REGENERATION");
        assertTrue(job.regenerating());
        assertFalse(job.terminal());
    }

    @Test
    void 최종_실패_코드는_errorCode를_쓰고_없으면_code를_쓴다() {
        assertEquals("GENERATION_REJECTED", job("failed", "GENERATION_REJECTED", "GENERATION_REJECTED").failureCode());
        assertEquals("GENERATION_TIMEOUT", job("failed", null, "GENERATION_TIMEOUT").failureCode());
        // code 필드가 생기기 전 응답
        assertEquals("GENERATION_FAILED", job("failed", "GENERATION_FAILED", null).failureCode());
    }

    @Test
    void 대기_생성_성공은_재생성이_아니다() {
        for (String status : new String[]{"queued", "generating", "succeeded"}) {
            assertFalse(job(status, null, null).regenerating(), status);
        }
    }

    private static GenerationJobResponse job(String status, String errorCode, String code) {
        return new GenerationJobResponse(UUID.randomUUID(), status, 1, null, errorCode, code);
    }
}
