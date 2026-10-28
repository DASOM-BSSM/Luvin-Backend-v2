package com.luvin.ai.controller;

import com.luvin.ai.dto.AiMessageLikeView;
import com.luvin.ai.dto.story.AiChoiceHttpRequest;
import com.luvin.ai.dto.story.AiChoiceOutcomeView;
import com.luvin.ai.dto.story.AiEpisodeContentView;
import com.luvin.ai.dto.story.AiEpisodeUnlockView;
import com.luvin.ai.dto.story.AiStoryFeedbackHttpRequest;
import com.luvin.ai.dto.story.AiStoryFeedbackView;
import com.luvin.ai.dto.story.AiStoryLikeHttpRequest;
import com.luvin.ai.dto.story.AiStoryStateView;
import com.luvin.ai.service.story.AiStoryService;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.ai.dto.AiInsufficientTokensErrorView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Tag(name = "러빈지옥 회차", description = "장면 블록 회차(1~10화) 콘텐츠, 선택, 완료, 1:1 장면 피드백·좋아요")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/seasons/episodes")
public class AiStoryController {

    private final AiStoryService storyService;

    @Operation(summary = "회차 블록 목록", description = """
            선택을 마친 회차면 선택 결과 블록(`choice_result`, 거절이면 뒤이은 대체 `pair`)이 `choice` 블록 바로 뒤에 들어 있다
            (choices 응답과 같은 순서, 완료한 회차를 다시 볼 때도 같다).
            저장된 회차가 있으면 그대로, 없으면 생성을 시작하고 `status: generating`으로 응답한다(1~3초 간격으로 다시 요청).
            실패하면 그다음 요청 한 번은 `failed`(errorCode 포함)로 응답하고, 그 뒤에 다시 요청하면 생성을 다시 시도한다. 1화 요청이 새 흐름의 시작이다(나이로 배경 결정, 1화에 stageIntro 포함).

            **409 code**
            - `AGE_REQUIRED`: 나이 또는 성별이 없다 — 둘 다 입력(PUT /api/users/me `{ age, gender }`) 후 다시 요청
            - `STORY_NOT_STARTED`: 1화부터 요청해야 한다
            - `PREVIOUS_EPISODE_NOT_COMPLETED`: 이전 회차를 먼저 완료해야 한다(선택 회차는 선택 후 완료)
            - `EPISODE_NOT_RELEASED`: 아직 공개 전
            - `SEASON_COMPLETED`: 끝난 시즌에서 생성하지 않은 회차
            - `EPISODE_NOT_UNLOCKED`: 열지 않은 4~10화 — 회차 열기(POST /{order}/unlock) 후 다시 요청
            """)
    @GetMapping("/{order}/content")
    public ApiResponse<AiEpisodeContentView> getContent(@PathVariable("order") int order) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(storyService.getContent(memberId, order));
    }

    @Operation(summary = "회차 열기", description = """
            1~3화는 무료라 차감 없이 열린 응답이다. 4~10화는 처음 열 때만 토큰(`story.episodeTokenCost`)을 쓰고, 이미 연 회차는
            차감 없이 같은 응답이다. 차감은 생성 전에 하고, 열린 뒤 생성이 실패해도 열린 상태로 남는다.
            순서: 이전 회차 완료 → 회차 열기 → content 요청. 열지 않은 4~10화의 content는 409 `EPISODE_NOT_UNLOCKED`.

            **오류**: 409 `INSUFFICIENT_TOKENS`(data: required, balance — 미니게임으로 안내), `PREVIOUS_EPISODE_NOT_COMPLETED`,
            `STORY_NOT_STARTED`, `SEASON_COMPLETED`
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "INSUFFICIENT_TOKENS면 data에 required(필요 토큰)·balance(현재 잔액). 다른 409 code는 data가 null",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AiInsufficientTokensErrorView.class)))
    @PostMapping("/{order}/unlock")
    public ApiResponse<AiEpisodeUnlockView> unlock(@PathVariable("order") int order) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(storyService.unlock(memberId, order));
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

    @Operation(summary = "1:1 장면 피드백", description = """
            1:1 장면(상대가 있는 `pair` 블록)에서 내 빵의 대사(독백 제외) 하나에 피드백을 남긴다. 회차 본문 pair 블록과 선택 거절 뒤
            대체 pair 블록(`choices` 응답의 blocks)에 남길 수 있고, 혼자인 독백 장면·그룹·메신저·인터뷰·선택 결과에는 안 된다.
            메시지당 하나이고 다시 보내면 글을 바꾼다. body의 `order`는 생략해도 되고, 보내면 경로의 order와 같아야 한다.
            남긴 피드백은 다음 회차 생성 입력(learnedProfile.feedbacks)으로 쓰인다.

            **400 code**
            - `SCENE_ORDER_MISMATCH`: body의 order가 경로의 order와 다르다
            - `SCENE_BLOCK_NOT_FOUND`: 이 회차에 없는 blockId
            - `SCENE_NOT_PAIR`: pair 블록(1:1 장면)이 아니다
            - `SCENE_MESSAGE_NOT_FOUND`: 그 블록에 없는 messageId
            - `SCENE_NOT_MY_LINE`: 내 빵의 대사가 아니다(상대 대사·내레이션)
            - `SCENE_MONOLOGUE`: 혼자인 독백 장면이거나 독백 대사다
            - `FEEDBACK_TOO_LONG`: text가 200자를 넘는다

            **409 code**: `CONTENT_NOT_READY`, `STORY_NOT_STARTED`
            """)
    @PostMapping("/{order}/scene-feedback")
    public ApiResponse<AiStoryFeedbackView> submitFeedback(@PathVariable("order") int order,
                                                           @Valid @RequestBody AiStoryFeedbackHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(storyService.submitFeedback(memberId, order, request.order(), request.blockId(),
                request.messageId(), request.text()));
    }

    @Operation(summary = "1:1 장면 좋아요", description = """
            1:1 장면(상대가 있는 `pair` 블록)에서 내 빵의 대사 좋아요를 켜고 끈다(`liked`). 상대 대사·독백·내레이션은 400.
            좋아요한 대사는 다음 회차 생성 입력(learnedProfile.likedMessages)으로 쓰인다.

            **400 code**: `SCENE_ORDER_MISMATCH`, `SCENE_BLOCK_NOT_FOUND`, `SCENE_NOT_PAIR`, `SCENE_MESSAGE_NOT_FOUND`,
            `SCENE_NOT_MY_LINE`, `SCENE_MONOLOGUE`(피드백과 같은 뜻) / 409 `CONTENT_NOT_READY`, `STORY_NOT_STARTED`
            """)
    @PostMapping("/{order}/scene-likes")
    public ApiResponse<AiMessageLikeView> setLike(@PathVariable("order") int order,
                                                  @Valid @RequestBody AiStoryLikeHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(storyService.setLike(memberId, order, request.order(), request.blockId(),
                request.messageId(), request.liked()));
    }

    @Operation(summary = "회차 완료", description = """
            관계 기록을 갱신하고(정리 단계) 회차 쪽지를 만든다. 10화면 최종 리포트를 만들고 시즌을 완료한다.
            AI 생성을 여러 번 기다려 수십 초가 걸리므로 **접수만 하고 바로 응답한다**(비동기).

            - 응답의 `completion`이 `{"order":N,"status":"completing"}`이면 진행 중이다.
              `GET /api/ai/seasons/me`의 `story.completion`을 2~3초 간격으로 폴링한다.
            - 끝나면 `completion`이 null이 되고 `completedOrders`에 N이 들어간다(다음 회차 진행 가능).
            - `status`가 `failed`(`errorCode` `STORY_GENERATION_FAILED`)면 아무것도 저장되지 않았다.
              이 API를 다시 보내면 처음부터 다시 시도한다.
            - 완료 중에 다시 보내도 새로 시작하지 않고 진행 상태만 돌려준다. 이미 완료한 회차면 상태만 돌려준다.

            **오류**: 409 `CONTENT_NOT_READY`, `CHOICE_REQUIRED`(선택 회차인데 선택 전), `STORY_NOT_STARTED`,
            `AGE_REQUIRED`
            """)
    @PostMapping("/{order}/complete")
    public ApiResponse<AiStoryStateView> complete(@PathVariable("order") int order) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(storyService.complete(memberId, order));
    }
}
