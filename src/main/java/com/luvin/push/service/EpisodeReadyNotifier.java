package com.luvin.push.service;

import com.luvin.push.client.PushClient;
import com.luvin.push.client.PushMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 러빈지옥 회차 생성이 끝났을 때 그 사용자의 모든 기기로 알림을 한 번 보낸다. 같은 회차에 두 번 보내지 않는 것은 호출부
 * (회차 생성 저장 트랜잭션의 ready_notified_at)가 보장한다. 알림 실패는 회차 생성 결과에 영향을 주지 않는다(로그만).
 */
@Service
@RequiredArgsConstructor
public class EpisodeReadyNotifier {

    private static final Logger log = LoggerFactory.getLogger(EpisodeReadyNotifier.class);
    static final String TYPE = "EPISODE_READY";
    static final String TITLE = "새 에피소드가 구워졌어요 🥐";

    private final PushTokenService pushTokenService;
    private final PushClient pushClient;

    /** @param seasonId GET /api/ai/seasons/me의 seasonId(UUID)와 같은 값 */
    public void notifyEpisodeReady(Long memberId, UUID seasonId, int order) {
        try {
            List<String> tokens = pushTokenService.tokensOf(memberId);
            if (tokens.isEmpty()) {
                return;
            }
            Map<String, Object> data = Map.of("type", TYPE, "order", order, "seasonId", seasonId.toString());
            String body = order + "화가 준비됐어요. 지금 확인해 보세요!";
            List<PushMessage> messages = tokens.stream().map(t -> new PushMessage(t, TITLE, body, data)).toList();
            Set<String> unregistered = pushClient.send(messages);
            pushTokenService.removeTokens(unregistered);
        } catch (Exception e) {
            log.warn("episode ready push failed: memberId={}, order={}, cause={}", memberId, order,
                    e.getClass().getSimpleName());
        }
    }
}
