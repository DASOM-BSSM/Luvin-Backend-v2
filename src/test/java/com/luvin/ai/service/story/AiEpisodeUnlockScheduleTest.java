package com.luvin.ai.service.story;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiEpisodeUnlockScheduleTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final OffsetDateTime NOV_26_MIDNIGHT = OffsetDateTime.parse("2026-11-26T00:00:00+09:00");

    @Test
    void 완료한_날의_다음_자정에_열린다() {
        // 23:59, 00:00 정각, 00:01, 00:10, 23:50 완료 모두 다음 날 00:00(KST)
        for (String completed : new String[]{"2026-11-25T00:00:00", "2026-11-25T00:01:00", "2026-11-25T00:10:00",
                "2026-11-25T23:50:00", "2026-11-25T23:59:00", "2026-11-25T23:59:59.999999"}) {
            OffsetDateTime at = AiEpisodeUnlockSchedule.waitingUntil(LocalDateTime.parse(completed), KST,
                    at("2026-11-25T23:59:59.999999+09:00"));
            assertEquals(NOV_26_MIDNIGHT, at, completed);
            assertEquals(ZoneOffset.ofHours(9), at.getOffset());
        }
    }

    @Test
    void 전날_23시59분에_끝냈으면_자정부터_열린다() {
        LocalDateTime completed = LocalDateTime.parse("2026-11-24T23:59:00");
        OffsetDateTime expected = OffsetDateTime.parse("2026-11-25T00:00:00+09:00");
        // 자정 1초 전: 아직 대기
        assertEquals(expected, AiEpisodeUnlockSchedule.waitingUntil(completed, KST, at("2026-11-24T23:59:59+09:00")));
        // 자정 정각부터 열린다
        assertNull(AiEpisodeUnlockSchedule.waitingUntil(completed, KST, at("2026-11-25T00:00:00+09:00")));
        assertNull(AiEpisodeUnlockSchedule.waitingUntil(completed, KST, at("2026-11-25T00:01:00+09:00")));
    }

    @Test
    void 자정_정각과_1분_뒤에_끝내면_그날은_못_열고_다음_자정에_열린다() {
        for (String completed : new String[]{"2026-11-25T00:00:00", "2026-11-25T00:01:00"}) {
            LocalDateTime time = LocalDateTime.parse(completed);
            assertEquals(NOV_26_MIDNIGHT,
                    AiEpisodeUnlockSchedule.waitingUntil(time, KST, at("2026-11-25T23:59:59+09:00")), completed);
            assertNull(AiEpisodeUnlockSchedule.waitingUntil(time, KST, at("2026-11-26T00:00:00+09:00")), completed);
        }
    }

    @Test
    void 서버_시간대가_UTC여도_날짜는_서울_기준으로_자른다() {
        // UTC 11/25 14:59 = KST 11/25 23:59 → 11/26 00:00 KST
        assertEquals(NOV_26_MIDNIGHT, AiEpisodeUnlockSchedule.waitingUntil(
                LocalDateTime.parse("2026-11-25T14:59:00"), UTC, at("2026-11-25T14:59:30Z")));
        // UTC 11/25 15:00 = KST 11/26 00:00 → 11/27 00:00 KST (UTC 날짜로 자르면 11/26 09:00이 되어 틀린다)
        assertEquals(OffsetDateTime.parse("2026-11-27T00:00:00+09:00"), AiEpisodeUnlockSchedule.waitingUntil(
                LocalDateTime.parse("2026-11-25T15:00:00"), UTC, at("2026-11-26T12:00:00Z")));
    }

    @Test
    void 완료_전이면_null() {
        assertNull(AiEpisodeUnlockSchedule.waitingUntil(null, KST, Instant.now()));
    }

    private static Instant at(String offsetDateTime) {
        return OffsetDateTime.parse(offsetDateTime).toInstant();
    }
}
