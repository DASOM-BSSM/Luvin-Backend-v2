package com.luvin.ai.dto.story;

/** 소금빵 회차 열기 대기 시간 변경. cooldown 예: "24h", "3d", "90m", "PT24H". 0 이상 30일 이하. */
public record AiEpisodeCooldownChangeRequest(String cooldown) {
}
