package com.luvin.ai.client.story;

import java.util.UUID;

/** 진행 중인 생성 job의 상태. regenerating이면 AI가 첫 시도를 검증에서 버리고 다시 쓰는 중이다. */
public record GenerationProgress(UUID jobId, String status, int attempt, boolean regenerating) {
}
