package com.luvin.ai.controller;

import com.luvin.ai.dto.story.AiChoiceHttpRequest;
import com.luvin.ai.dto.story.AiChoiceOutcomeView;
import com.luvin.ai.dto.story.AiEpisodeContentView;
import com.luvin.ai.dto.story.AiStoryStateView;
import com.luvin.ai.service.story.AiStoryService;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 러빈지옥 장면 블록 회차(1~10화). 시즌 상태는 기존 GET /api/ai/seasons/me 응답의 story에 담긴다.
 * 쪽지(GET /letters/unread)와 최종 리포트(GET /report)는 기존 경로·응답 형식 그대로다.
 */
@Tag(name = "러빈지옥 회차", description = "장면 블록 회차(1~10화) 콘텐츠, 선택, 완료")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/seasons/episodes")
public class AiStoryController {

    private final AiStoryService storyService;

    @Operation(summary = "회차 블록 목록", description = """
            저장된 회차가 있으면 그대로, 없으면 생성을 시작하고 `status: generating`으로 응답한다(1~3초 간격으로 다시 요청).
            `failed`면 다시 요청할 때 생성을 다시 시도한다. 1화 요청이 새 흐름의 시작이다(나이로 배경 결정, 1화에 stageIntro 포함).

            **409 code**
            - `AGE_REQUIRED`: 나이가 없다 — 나이 입력 후 다시 요청
            - `STORY_NOT_STARTED`: 1화부터 요청해야 한다
            - `PREVIOUS_EPISODE_NOT_COMPLETED`: 이전 회차를 먼저 완료해야 한다(선택 회차는 선택 후 완료)
            - `EPISODE_NOT_RELEASED`: 아직 공개 전
            - `SEASON_COMPLETED`: 끝난 시즌에서 생성하지 않은 회차
            """)
    @GetMapping("/{order}/content")
    public ApiResponse<AiEpisodeContentView> getContent(@PathVariable("order") int order) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(storyService.getContent(memberId, order));
    }

    @Operation(summary = "선택 처리 (3·5·6·8화)", description = """
            body `{ pointId, breadId | null }` — breadId가 null이면 혼자 있기. 수락·거절은 서버 코드 규칙으로 정한다.
            응답은 선택 결과 장면(`choice_result`, 거절이면 대체 장면 포함)과 갱신된 시즌 상태다.
            같은 선택을 다시 보내면 저장된 결과를 그대로 돌려준다.

            **오류**: 400 회차와 pointId 불일치·후보가 아닌 breadId / 409 `CONTENT_NOT_READY`, `CHOICE_ALREADY_MADE`,
            `STORY_NOT_STARTED` / 502 `STORY_GENERATION_FAILED`(결과 장면 생성 실패, 다시 요청 가능)
            """)
    @PostMapping("/{order}/choices")
    public ApiResponse<AiChoiceOutcomeView> choose(@PathVariable("order") int order,
                                                   @Valid @RequestBody AiChoiceHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(storyService.choose(memberId, order, request.pointId(), request.breadId()));
    }

    @Operation(summary = "회차 완료", description = """
            관계 기록을 갱신하고(정리 단계) 회차 쪽지를 만든다. 10화면 최종 리포트를 만들고 시즌을 완료한다.
            이미 완료한 회차면 상태만 돌려준다.

            **오류**: 409 `CONTENT_NOT_READY`, `CHOICE_REQUIRED`(선택 회차인데 선택 전), `STORY_NOT_STARTED`
            / 502 `STORY_GENERATION_FAILED`(아무것도 저장되지 않음, 다시 요청 가능)
            """)
    @PostMapping("/{order}/complete")
    public ApiResponse<AiStoryStateView> complete(@PathVariable("order") int order) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(storyService.complete(memberId, order));
    }
}
