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
    /**
     * 다시 굽기(reroll)는 제거됐다. 이미 저장된 ai_idempotency_keys row가 이 값을 갖고 있어 읽을 수 있도록
     * 값만 남긴다(새로 발급하지 않는다). 값을 지우려면 그 row를 먼저 정리하는 마이그레이션이 필요하다.
     */
    @Deprecated
    EPISODE_REROLL,
    PARTNER_LETTER_GENERATION
}
