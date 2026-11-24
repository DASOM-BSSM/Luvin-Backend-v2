package com.luvin.ai.service.exception;

import java.time.OffsetDateTime;

/** 소금빵으로 4~10화를 열려는데 앞 회차를 끝낸 다음 자정(KST)이 아직 오지 않았다(409 EPISODE_COOLDOWN). */
public class AiEpisodeCooldownException extends RuntimeException {

    public static final String CODE = "EPISODE_COOLDOWN";

    private final OffsetDateTime nextUnlockAt;

    public AiEpisodeCooldownException(OffsetDateTime nextUnlockAt) {
        super("아직 소금빵으로 열 수 없어요. 식빵으로 바로 열거나 기다려 주세요.");
        this.nextUnlockAt = nextUnlockAt;
    }

    public OffsetDateTime getNextUnlockAt() {
        return nextUnlockAt;
    }
}
