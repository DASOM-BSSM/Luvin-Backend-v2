package com.luvin.ai.controller;

import com.luvin.ai.service.AiJobEventStreamService;
import com.luvin.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * generation/reroll/쪽지 job의 진행 이벤트를 SSE로 받는다. jobId는 각 요청 응답(jobId 필드)에 있는 값이다.
 * 이벤트 처리 규칙은 {@link AiJobEventStreamService} 참고. SSE는 보조 수단이고 결과 확정은 기존 조회 API로 한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/jobs")
public class AiJobController {

    private final AiJobEventStreamService jobEventStreamService;

    /**
     * 재연결할 때는 마지막으로 받은 이벤트 id를 Last-Event-ID 헤더로 보낸다. 헤더를 못 붙이는 클라이언트는
     * after_event_id query로 보내도 된다(둘 다 있으면 헤더 우선). 내 job이 아니면 404.
     */
    @GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobEvents(
            @PathVariable UUID jobId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(value = "after_event_id", required = false) String afterEventId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return jobEventStreamService.open(memberId, jobId, lastEventId != null ? lastEventId : afterEventId);
    }
}
