package com.luvin.push.client;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuietHoursPushClientTest {

    private static final List<PushMessage> ONE = List.of(new PushMessage("t", "제목", "본문", Map.of(), null));

    @Test
    void 한국_시간_06시부터_23시59분까지만_보내고_자정부터_05시59분까지는_버린다() {
        for (String time : List.of("06:00:00", "12:00:00", "23:59:59")) {
            assertEquals(1, sentAt("2026-11-25T" + time + "+09:00"), time);
        }
        for (String time : List.of("00:00:00", "03:00:00", "05:59:59")) {
            assertEquals(0, sentAt("2026-11-25T" + time + "+09:00"), time);
        }
    }

    @Test
    void 서버_시계가_UTC여도_한국_시간으로_판단한다() {
        // UTC 20:59 = KST 05:59(버림), UTC 21:00 = KST 06:00(보냄)
        assertEquals(0, sentAt("2026-11-24T20:59:00Z"));
        assertEquals(1, sentAt("2026-11-24T21:00:00Z"));
    }

    @Test
    void 버리면_지울_토큰도_없다() {
        PushClient gone = messages -> Set.of("t");
        Clock night = Clock.fixed(OffsetDateTime.parse("2026-11-25T03:00:00+09:00").toInstant(), ZoneOffset.UTC);
        assertTrue(new QuietHoursPushClient(gone, night).send(ONE).isEmpty());
    }

    private static int sentAt(String instant) {
        List<PushMessage> sent = new ArrayList<>();
        PushClient recorder = messages -> {
            sent.addAll(messages);
            return Set.of();
        };
        Clock clock = Clock.fixed(OffsetDateTime.parse(instant).toInstant(), ZoneOffset.UTC);
        new QuietHoursPushClient(recorder, clock).send(ONE);
        return sent.size();
    }
}
