package com.luvin.push.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * 모든 푸시의 발송 시간 제한. 한국 시간 06:00~23:59에만 실제 클라이언트로 넘기고, 00:00~05:59에 생긴 알림은 보내지 않고
 * 버린다(쌓아 두었다가 아침에 보내지 않는다). 알림 종류마다 따로 거르지 않고 PushClient 앞 한 곳에서 거른다.
 */
public class QuietHoursPushClient implements PushClient {

    private static final Logger log = LoggerFactory.getLogger(QuietHoursPushClient.class);
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    /** 이 시각(KST)부터 보낸다. 자정부터 이 시각 전까지는 버린다. */
    public static final LocalTime SEND_FROM = LocalTime.of(6, 0);

    private final PushClient delegate;
    private final Clock clock;

    public QuietHoursPushClient(PushClient delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    @Override
    public Set<String> send(List<PushMessage> messages) {
        if (messages.isEmpty()) {
            return Set.of();
        }
        if (!sendable(clock)) {
            log.info("push dropped (quiet hours 00:00-05:59 KST): messages={}", messages.size());
            return Set.of();
        }
        return delegate.send(messages);
    }

    static boolean sendable(Clock clock) {
        return !LocalTime.now(clock.withZone(ZONE)).isBefore(SEND_FROM);
    }
}
