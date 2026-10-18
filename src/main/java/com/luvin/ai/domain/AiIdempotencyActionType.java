package com.luvin.ai.domain;

/**
 * 사용자 동작 종류별 idempotency key 관리 단위.
 * 한 사용자 동작을 재시도할 때는 같은 key를 재사용하고, 새로운 동작에만 새 key를 발급한다 (요구사항 3.2).
 */
public enum AiIdempotencyActionType {
    SEASON_CREATE,
    SEASON_CREATE_NEXT,
    EPISODE_GENERATION,
    EPISODE_SELECTION,
    EPISODE_REROLL,
    PARTNER_LETTER_GENERATION
}
