package com.luvin.ai.client.story;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * POST /luvin/generation-jobs · GET /luvin/generation-jobs/{jobId} 응답. status는 queued | generating | regenerating
 * | succeeded | failed. result는 succeeded일 때만 있고 기존 동기 endpoint 응답과 같은 JSON이다.
 */
public record GenerationJobResponse(UUID jobId, String status, int attempt, JsonNode result, String errorCode) {

    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";

    public boolean terminal() {
        return SUCCEEDED.equals(status) || FAILED.equals(status);
    }
}
