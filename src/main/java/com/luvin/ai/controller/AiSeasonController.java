package com.luvin.ai.controller;

import com.luvin.ai.dto.AiEpisodeFeedbackView;
import com.luvin.ai.dto.AiEpisodeMessagesView;
import com.luvin.ai.dto.AiEpisodeProgressView;
import com.luvin.ai.dto.AiMessageLikeView;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.AiRerollView;
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
import com.luvin.ai.dto.RerollHttpRequest;
import com.luvin.ai.dto.StartNextSeasonRequest;
import com.luvin.ai.service.AiSeasonOrchestrationService;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
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

    /**
     * 현재 시즌이 COMPLETED일 때만 다음 시즌을 생성한다(첫 시즌은 /from-survey, 2번째부터는 이 경로).
     * 무료 횟수가 남아있으면 그걸, 없으면 토큰 1개를 시즌 생성과 같은 트랜잭션에서 차감한다.
     */
    @PostMapping("/next")
    public ApiResponse<AiSeasonStatusView> startNextSeason(
            @RequestBody(required = false) StartNextSeasonRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        StartNextSeasonRequest safeRequest = request != null ? request : new StartNextSeasonRequest(null, null);
        return ApiResponse.ok(orchestrationService.startNextSeason(memberId, safeRequest));
    }

    /** 이 사용자의 모든 시즌(지난 시즌 포함)을 seasonNumber 오름차순으로 반환한다. */
    @GetMapping
    public ApiResponse<List<AiSeasonSummaryView>> listSeasons() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.listSeasons(memberId));
    }

    @PostMapping("/episodes/{number}/generations")
    public ApiResponse<AiEpisodeProgressView> requestGeneration(@PathVariable("number") int episodeNumber) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.requestGeneration(memberId, episodeNumber));
    }

    @PostMapping("/episodes/1/selection")
    public ApiResponse<AiSelectionView> submitEpisode1Selection(@Valid @RequestBody Episode1SelectionHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.submitEpisode1Selection(memberId, request.partnerId()));
    }

    @PostMapping("/episodes/3/selection")
    public ApiResponse<AiSelectionView> submitEpisode3Result(@RequestBody Episode3ResultHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.submitEpisode3MinigameResult(
                memberId, request.success(), request.partnerId()));
    }

    @PostMapping("/episodes/{number}/rerolls")
    public ApiResponse<AiRerollView> requestReroll(@PathVariable("number") int episodeNumber,
                                                    @Valid @RequestBody RerollHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.requestReroll(memberId, episodeNumber, request.partnerId()));
    }

    /**
     * reroll 완료 여부 폴링용. 같은 회차로 reroll을 요청한 적이 없으면 404.
     * season_id를 생략하면 현재(최신) 시즌, 지정하면 본인 소유의 그 시즌(지난 시즌 포함)을 조회한다.
     */
    @GetMapping("/episodes/{number}/rerolls")
    public ApiResponse<AiRerollView> getRerollStatus(
            @PathVariable("number") int episodeNumber,
            @RequestParam(value = "season_id", required = false) UUID seasonId) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.getRerollStatus(memberId, seasonId, episodeNumber));
    }

    /** season_id를 생략하면 현재(최신) 시즌, 지정하면 본인 소유의 그 시즌(지난 시즌 포함)을 조회한다. */
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
     * 안 읽은 "SECRET LOVE" 쪽지 조회(최신 1개만). 없으면 data가 null인 200 응답을 돌려준다
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
