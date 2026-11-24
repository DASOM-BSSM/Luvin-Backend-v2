package com.luvin.ai.service.story;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 소금빵으로 4~10화를 열 수 있게 되는 시각: 앞 회차를 서버가 완료 처리한 날의 다음 자정(Asia/Seoul 00:00).
 * 예) 11/25 23:50 완료 → 11/26 00:00, 11/25 00:10 완료 → 11/26 00:00, 11/25 00:00 정각 완료 → 11/26 00:00.
 *
 * <p>story.nextUnlockAt과 409 EPISODE_COOLDOWN 판정이 모두 {@link #waitingUntil}을 쓴다 — 응답과 판정이 어긋나지 않는다.
 * 서버 기본 시간대와 상관없이 날짜는 항상 Asia/Seoul로 자른다.
 */
public final class AiEpisodeUnlockSchedule {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private AiEpisodeUnlockSchedule() {
    }

    /**
     * 앞 회차 완료 시각 기준으로 아직 기다려야 하면 열리는 시각(+09:00 오프셋), 이미 열 수 있으면 null.
     *
     * @param completedAt 앞 회차를 서버가 완료 처리한 시각(DB 값, serverZone 기준 LocalDateTime). null이면 null
     * @param serverZone  completedAt이 저장된 시간대(서버 JVM 기본 시간대)
     * @param now         지금
     */
    public static OffsetDateTime waitingUntil(LocalDateTime completedAt, ZoneId serverZone, Instant now) {
        if (completedAt == null) {
            return null;
        }
        OffsetDateTime unlockAt = unlockAt(completedAt.atZone(serverZone).toInstant());
        return now.isBefore(unlockAt.toInstant()) ? unlockAt : null;
    }

    /** 완료 시각이 속한 날(Asia/Seoul)의 다음 자정. */
    static OffsetDateTime unlockAt(Instant completedAt) {
        return completedAt.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();
    }
}
