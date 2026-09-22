package com.luvin.ai.client.dto;

/**
 * POST /v1/seasons 요청 body. pool_version/topic_config_version은 현재 계약상 고정값만 사용한다 (요구사항 5.1).
 */
public record CreateSeasonRequestDto(
        CharacterProfileDto representative,
        String poolVersion,
        String topicConfigVersion
) {
    public static final String POOL_VERSION = "pool.v1";
    public static final String TOPIC_CONFIG_VERSION = "topics.v1";

    public static CreateSeasonRequestDto of(CharacterProfileDto representative) {
        return new CreateSeasonRequestDto(representative, POOL_VERSION, TOPIC_CONFIG_VERSION);
    }
}
