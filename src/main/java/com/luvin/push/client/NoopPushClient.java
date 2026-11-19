package com.luvin.push.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/** app.push.client=noop: 보내지 않고 로그만 남긴다(테스트·로컬). */
public class NoopPushClient implements PushClient {

    private static final Logger log = LoggerFactory.getLogger(NoopPushClient.class);

    @Override
    public Set<String> send(List<PushMessage> messages) {
        log.info("push skipped (noop client): messages={}", messages.size());
        return Set.of();
    }
}
