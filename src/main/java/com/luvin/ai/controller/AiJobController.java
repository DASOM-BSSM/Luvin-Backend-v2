package com.luvin.ai.controller;

import com.luvin.ai.service.AiJobEventStreamService;
import com.luvin.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * generation/쪽지 job의 진행 이벤트를 SSE로 받는다. jobId는 각 요청 응답(jobId 필드)에 있는 값이다.
 * 이벤트 처리 규칙은 {@link AiJobEventStreamService} 참고. SSE는 보조 수단이고 결과 확정은 기존 조회 API로 한다.
 */
@Tag(name = "AI job 진행 이벤트", description = "generation job 진행 상황 SSE 구독")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/jobs")
public class AiJobController {

    private static final String STREAM_DESCRIPTION = """
            generation 요청 응답의 `jobId`로 연결한다. 헤더: `Authorization: Bearer <토큰>`, `Accept: text/event-stream`.
            `Last-Event-ID` 없이 연결하면 그 job의 첫 이벤트부터 전부 받는다(이미 끝난 job이면 전부 받은 뒤 종료 이벤트로 닫힘).
            재연결할 때만 마지막으로 받은 `id`를 `Last-Event-ID` 헤더(또는 `after_event_id` query)로 보낸다.

            **이벤트** (data는 snake_case JSON, `segment`: `scene` | `candidates` | `one_to_one` | `report`)
            - `segment_start` `{"segment":"scene"}`
            - `delta` `{"segment":"scene","text":"{\\"messages\\":["}` — text는 검증 전 JSON 조각. 화면에 그대로 표시하지 않는다.
            - `segment_end` `{"segment":"scene","message_count":45}` — 그 구간 검증 완료. 아직 조회 API에는 없다.
            - `segment_error` `{"segment":"scene","error_code":"INVALID_OUTPUT"}` — 그 구간 버퍼를 버린다(`STREAM_FAILED`도 있음).
            - `completed` `{"result_version_id":"<uuid>","revision":2}` — 이 이벤트를 받은 뒤 조회 API로 결과를 가져온다.
              서버가 결과를 DB에 반영한 뒤 보내므로 바로 조회된다. 반영이 드물게 실패하면 조회가 409일 수 있으니 잠시 후 다시 조회한다.
              job 상태로 보충 생성된 종료 이벤트면 `{"status":"succeeded","result_version_id":"<uuid>","error_code":null}`.
            - `error` `{"error_code":"STALE_JOB"}` — job 실패.
            - `stream_error` `{"error_code":"..."}` — 백엔드가 보내는 중계 실패.
              `INVALID_LAST_EVENT_ID`(id 없이 재연결), `JOB_NOT_FOUND`(polling 확인),
              `AI_UNAVAILABLE` / `STREAM_INTERRUPTED` / `INTERNAL_ERROR`(마지막 id로 재연결 또는 polling).
            - 15초마다 `: keep-alive` 주석. `completed` / `error` / `stream_error` 뒤에는 서버가 연결을 닫으니 클라이언트도 닫는다.

            **오류 응답** (SSE가 아니라 JSON)
            - 401 토큰 없음/만료: `{"success":false,"data":null,"message":"인증이 필요합니다. 로그인 후 다시 시도해주세요.","code":null}`
            - 404 내 job이 아님/없는 job
            - 503 서버당 동시 스트림 한도 초과
            """;

    private final AiJobEventStreamService jobEventStreamService;

    /**
     * 재연결할 때는 마지막으로 받은 이벤트 id를 Last-Event-ID 헤더로 보낸다. 헤더를 못 붙이는 클라이언트는
     * after_event_id query로 보내도 된다(둘 다 있으면 헤더 우선). 내 job이 아니면 404.
     */
    @Operation(summary = "job 진행 이벤트 구독 (SSE)", description = STREAM_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 스트림",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "401", description = "토큰 없음/만료",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(
                            value = "{\"success\":false,\"data\":null,\"message\":\"인증이 필요합니다. 로그인 후 다시 시도해주세요.\",\"code\":null}"))),
            @ApiResponse(responseCode = "404", description = "내 job이 아니거나 없는 job",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(
                            value = "{\"success\":false,\"data\":null,\"message\":\"job을 찾을 수 없습니다: <jobId>\",\"code\":null}"))),
            @ApiResponse(responseCode = "503", description = "서버당 동시 스트림 한도 초과 — 기존 polling으로 진행",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(
                            value = "{\"success\":false,\"data\":null,\"message\":\"요청이 많아 처리가 지연되고 있습니다. 잠시 후 다시 시도해주세요.\",\"code\":null}")))
    })
    @GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobEvents(
            @PathVariable UUID jobId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(value = "after_event_id", required = false) String afterEventId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return jobEventStreamService.open(memberId, jobId, lastEventId != null ? lastEventId : afterEventId);
    }
}
