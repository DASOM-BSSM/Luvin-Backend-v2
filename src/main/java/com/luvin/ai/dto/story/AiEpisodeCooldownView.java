package com.luvin.ai.dto.story;

import java.time.OffsetDateTime;

/** 409 EPISODE_COOLDOWN의 data. nextUnlockAt: 소금빵으로 열 수 있게 되는 시각. */
public record AiEpisodeCooldownView(OffsetDateTime nextUnlockAt) {
}
