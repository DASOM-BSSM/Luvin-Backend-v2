package com.luvin.ai.controller;

import com.luvin.ai.dto.AiEpisodeFeedbackView;
import com.luvin.ai.dto.AiEpisodeMessagesView;
import com.luvin.ai.dto.AiEpisodeProgressView;
import com.luvin.ai.dto.AiMessageLikeView;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.AiSeasonStatusView;
import com.luvin.ai.dto.AiSeasonSummaryView;
import com.luvin.ai.dto.AiSelectionView;
import com.luvin.ai.dto.CreateSeasonFromSurveyRequest;
import com.luvin.ai.dto.CreateSeasonHttpRequest;
import com.luvin.ai.dto.Episode1SelectionHttpRequest;
import com.luvin.ai.dto.Episode3ResultHttpRequest;
import com.luvin.ai.dto.EpisodeFeedbackHttpRequest;
import com.luvin.ai.dto.EpisodeMessageLikeHttpRequest;
import com.luvin.ai.dto.MarkSeenHttpRequest;
import com.luvin.ai.dto.PartnerLetterView;
import com.luvin.ai.dto.StartNextSeasonRequest;
import com.luvin.ai.service.AiSeasonOrchestrationService;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 모바일 앱은 AI 서비스를 직접 호출하지 않고 항상 이 컨트롤러를 경유한다 (요구사항 1절).
 *
 * 기존 5화 흐름의 회차 API(generations, 1화/3화 selection, 메시지 페이지 조회, 피드백, 좋아요)는 장면 블록 회차
 * ({@link AiStoryController})로 바뀌어 @Deprecated다. 동작은 그대로 두고, 프론트 전환이 끝나면 별도 작업으로 지운다.
 *
 * 3화 미니게임 결과 endpoint({@link #submitEpisode3Result})는 주의가 필요하다: success 판정과
 * 성공 대상 결정은 Spring이 담당해야 하며(요구사항 1절, 5.4), 이 컨트롤러가 요청 body의 success 값을
 * 그대로 신뢰해서는 안 된다. 실제 배선 시 미니게임 플레이 판정을 마친 서버 측 결과를 조회해서 넘기는
 * 서비스 계층이 이 앞단에 있어야 한다 (현재 이 저장소의 다른 미니게임 도메인이 이미 같은 문제를
 * 갖고 있어 함께 고쳐야 한다 — README 참고).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/seasons")
public class AiSeasonController {

    private final AiSeasonOrchestrationService orchestrationService;

    @PostMapping
    public ApiResponse<AiSeasonStatusView> createSeason(@Valid @RequestBody CreateSeasonHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.createSeason(memberId, request.representative()));
    }

    /**
     * 서버가 저장한 설문 v2 결과로 시즌을 생성한다. 전환 기간 동안 이 경로가 authoritative source가
     * 되도록 유도하고, 기존 POST(위 createSeason)는 내부 테스트 fixture 용도로만 남긴다.
     */
    @PostMapping("/from-survey")
    public ApiResponse<AiSeasonStatusView> createSeasonFromSurvey(@Valid @RequestBody CreateSeasonFromSurveyRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.createSeasonFromSurvey(memberId, request.surveyResultId()));
    }

    @GetMapping("/me")
    public ApiResponse<AiSeasonStatusView> getStatus() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.getStatus(memberId));
    }

    /** 10화를 끝낸 사용자가 다음 시즌(새 출연진)을 만든다. 첫 시즌은 /from-survey로 만든다. */
    @Operation(summary = "새로운 시즌 만들기", description = """
            가장 최근 시즌이 끝났으면(10화 완료, status COMPLETED) 다음 번호의 시즌을 새 출연진으로 만들고 바로 1화부터
            시작할 수 있는 상태로 돌려준다(seasonNumber = 직전 + 1, status IN_PROGRESS, currentEpisode 1,
            story.unlockedOrders [1,2,3], completedOrders·choices 빈 값, route null).
            배경(setting)은 나이에 맞는 배경 중 지난 시즌들에서 안 쓴 배경을, 다 썼으면 가장 오래전에 쓴 배경을 고른다(같은
            조건이면 랜덤) — 직전 시즌과 겹치지 않는다. 나이에 맞는 배경: 19세 이하 school·cafe, 20~29세 campus·cafe·office,
            30세 이상 office·cafe. (첫 시즌·시즌 초기화 뒤의 시즌은 from-survey에서 나이로 정한다.)
            시즌 만들기는 무료이고, 새 시즌도 1~3화는 무료, 4화부터 회차마다 `story.episodeTokenCost`만큼 토큰을 쓴다.

            - surveyResultId: 내 분신 성향 기준이 되는 최신 설문 결과. 생략하면 서버의 최신 설문 결과를 쓴다.
            - clientRequestId: 멱등 키. 같은 값으로 다시 오면 그 요청이 만든 시즌을 그대로 200으로 돌려준다. 동시에 여러 번
              와도 시즌은 하나만 생긴다.

            **오류**: 409 `SEASON_IN_PROGRESS`(가장 최근 시즌이 진행 중 — 그 시즌으로 들어가면 된다),
            409 `STORY_NOT_STARTED`(시즌이 하나도 없음), 409 `AGE_REQUIRED`(나이·성별 없음),
            409 `SURVEY_RESULT_STALE`(surveyResultId가 최신 설문 결과가 아님), 404 `SURVEY_RESULT_NOT_FOUND`
            (본인 설문 결과가 없음).""")
    @PostMapping("/next")
    public ApiResponse<AiSeasonStatusView> startNextSeason(
            @RequestBody(required = false) StartNextSeasonRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        StartNextSeasonRequest safeRequest = request != null ? request : new StartNextSeasonRequest(null, null);
        return ApiResponse.ok(orchestrationService.startNextSeason(memberId, safeRequest));
    }

    /** 이 사용자의 모든 시즌(지난 시즌 포함)을 최신 시즌부터(seasonNumber 내림차순) 반환한다. */
    @Operation(summary = "시즌 목록", description = "지난 시즌까지 최신 시즌부터(seasonNumber 내림차순) 돌려준다. "
            + "setting(배경)·settingTitle(카드 제목, 예: In School)을 함께 준다. 지난 시즌 리포트는 "
            + "GET /report?season_id=, 회차 다시 보기는 season_id를 받는 API를 쓴다.")
    @GetMapping
    public ApiResponse<List<AiSeasonSummaryView>> listSeasons() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.listSeasons(memberId));
    }

    @Deprecated
    @Operation(deprecated = true, description = "기존 5화 흐름 회차 생성. 새 흐름은 GET /episodes/{order}/content를 쓴다. 프론트 전환 후 삭제 예정.")
    @PostMapping("/episodes/{number}/generations")
    public ApiResponse<AiEpisodeProgressView> requestGeneration(@PathVariable("number") int episodeNumber) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.requestGeneration(memberId, episodeNumber));
    }

    @Deprecated
    @Operation(deprecated = true, description = "기존 5화 흐름 1화 선택. 새 흐름은 POST /episodes/{order}/choices를 쓴다. 프론트 전환 후 삭제 예정.")
    @PostMapping("/episodes/1/selection")
    public ApiResponse<AiSelectionView> submitEpisode1Selection(@Valid @RequestBody Episode1SelectionHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.submitEpisode1Selection(memberId, request.partnerId()));
    }

    @Deprecated
    @Operation(deprecated = true, description = "기존 5화 흐름 3화 미니게임 선택. 새 흐름에는 에피소드 안 미니게임이 없다. 프론트 전환 후 삭제 예정.")
    @PostMapping("/episodes/3/selection")
    public ApiResponse<AiSelectionView> submitEpisode3Result(@RequestBody Episode3ResultHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.submitEpisode3MinigameResult(
                memberId, request.success(), request.partnerId()));
    }

    /** season_id를 생략하면 현재(최신) 시즌, 지정하면 본인 소유의 그 시즌(지난 시즌 포함)을 조회한다. */
    @Deprecated
    @Operation(deprecated = true, description = "기존 5화 흐름 메시지 페이지 조회. 새 흐름은 GET /episodes/{order}/content가 블록 목록을 한 번에 내려준다. 프론트 전환 후 삭제 예정.")
    @GetMapping("/episodes/{number}")
    public ApiResponse<AiEpisodeMessagesView> getEpisodeMessages(
            @PathVariable("number") int episodeNumber,
            @RequestParam(value = "season_id", required = false) UUID seasonId,
            @RequestParam(value = "after_sequence", required = false) Integer afterSequence,
            @RequestParam(value = "limit", required = false) Integer limit) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(
                orchestrationService.getEpisodeMessages(memberId, seasonId, episodeNumber, afterSequence, limit));
    }

    @PostMapping("/episodes/{number}/seen")
    public ApiResponse<Void> markMessagesSeen(@PathVariable("number") int episodeNumber,
                                               @Valid @RequestBody MarkSeenHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        orchestrationService.markMessagesSeen(memberId, episodeNumber, request.sequence());
        return ApiResponse.okMessage("진행도가 저장되었습니다.");
    }

    /**
     * "나의 빵에게" — 2화/4화 1:1 대화 중 본인 AI 분신이 한 말 한 줄을 골라 피드백을 남긴다.
     * 회차당 1회만 가능하다(409 FEEDBACK_ALREADY_SUBMITTED). season_id를 생략하면 현재(최신) 시즌,
     * 지정하면 본인 소유의 그 시즌(지난 시즌 포함)을 대상으로 한다.
     */
    @Deprecated
    @Operation(deprecated = true, description = "기존 5화 흐름 2·4화 피드백. 새 흐름에서는 쓰지 않는다. 프론트 전환 후 삭제 예정.")
    @PostMapping("/episodes/{number}/feedback")
    public ApiResponse<AiEpisodeFeedbackView> submitEpisodeFeedback(
            @PathVariable("number") int episodeNumber,
            @RequestParam(value = "season_id", required = false) UUID seasonId,
            @Valid @RequestBody EpisodeFeedbackHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.submitEpisodeFeedback(
                memberId, seasonId, episodeNumber, request.messageId(), request.content()));
    }

    /**
     * 2화/4화 1:1 대화 메시지 하트를 토글한다(켜져 있으면 끄고, 꺼져 있으면 켠다). 본인 분신/상대
     * 메시지 모두 가능하고 개수 제한이 없다. season_id를 생략하면 현재(최신) 시즌, 지정하면 본인
     * 소유의 그 시즌(지난 시즌 포함)을 대상으로 한다.
     */
    @Deprecated
    @Operation(deprecated = true, description = "기존 5화 흐름 2·4화 좋아요. 새 흐름에서는 쓰지 않는다. 프론트 전환 후 삭제 예정.")
    @PostMapping("/episodes/{number}/likes")
    public ApiResponse<AiMessageLikeView> toggleEpisodeMessageLike(
            @PathVariable("number") int episodeNumber,
            @RequestParam(value = "season_id", required = false) UUID seasonId,
            @Valid @RequestBody EpisodeMessageLikeHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.toggleEpisodeMessageLike(
                memberId, seasonId, episodeNumber, request.messageId()));
    }

    /** season_id를 생략하면 현재(최신) 시즌, 지정하면 본인 소유의 그 시즌(지난 시즌 포함)을 조회한다. */
    @GetMapping("/report")
    public ApiResponse<AiReportView> getReport(
            @RequestParam(value = "season_id", required = false) UUID seasonId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.getReport(memberId, seasonId));
    }

    /**
     * 가장 최근 시즌의 안 읽은 "SECRET LOVE" 쪽지 조회(최신 1개만). 없으면 data가 null인 200 응답을 돌려준다
     * (별도 204 처리 없이, 이 컨트롤러 전반의 ApiResponse 래핑 컨벤션을 그대로 따른다).
     */
    @GetMapping("/letters/unread")
    public ApiResponse<PartnerLetterView> getUnreadLetter() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.getUnreadLetter(memberId));
    }

    @PostMapping("/letters/{letterId}/read")
    public ApiResponse<Void> markLetterRead(@PathVariable("letterId") Long letterId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        orchestrationService.markLetterRead(memberId, letterId);
        return ApiResponse.okMessage("쪽지를 읽음 처리했습니다.");
    }
}
