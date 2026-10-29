package com.luvin.ai.client.story;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * POST /luvin/generation-jobs · GET /luvin/generation-jobs/{jobId} 응답. status는 queued | generating | regenerating
 * | succeeded | failed. result는 succeeded일 때만 있고 기존 동기 endpoint 응답과 같은 JSON이다.
 *
 * code: 재생성 중이면 REGENERATION(진행 상태 — 실패로 처리하거나 새로 접수하지 않는다), failed면 errorCode와 같은 최종
 * 실패 코드, 그 외에는 null.
 */
public record GenerationJobResponse(UUID jobId, String status, int attempt, JsonNode result, String errorCode,
                                    String code) {

    public static final String SUCCEEDED = "succeeded";
    public static final String FAILED = "failed";
    public static final String REGENERATING = "regenerating";
    public static final String REGENERATION = "REGENERATION";

    public boolean terminal() {
        return SUCCEEDED.equals(status) || FAILED.equals(status);
    }

    public boolean regenerating() {
        return REGENERATING.equals(status) || REGENERATION.equals(code);
    }

    /** failed일 때의 최종 실패 코드. code를 주기 전 AI 응답(errorCode만 있음)도 받는다. */
    public String failureCode() {
        return errorCode != null ? errorCode : code;
    }
}
