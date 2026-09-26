package com.luvin.ai.controller;

import com.luvin.ai.dto.AiEpisodeMessagesView;
import com.luvin.ai.dto.AiEpisodeProgressView;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.AiRerollView;
import com.luvin.ai.dto.AiSeasonStatusView;
import com.luvin.ai.dto.AiSelectionView;
import com.luvin.ai.dto.CreateSeasonFromSurveyRequest;
import com.luvin.ai.dto.CreateSeasonHttpRequest;
import com.luvin.ai.dto.Episode1SelectionHttpRequest;
import com.luvin.ai.dto.Episode3ResultHttpRequest;
import com.luvin.ai.dto.MarkSeenHttpRequest;
import com.luvin.ai.dto.RerollHttpRequest;
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

    @GetMapping("/episodes/{number}")
    public ApiResponse<AiEpisodeMessagesView> getEpisodeMessages(
            @PathVariable("number") int episodeNumber,
            @RequestParam(value = "after_sequence", required = false) Integer afterSequence,
            @RequestParam(value = "limit", required = false) Integer limit) {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.getEpisodeMessages(memberId, episodeNumber, afterSequence, limit));
    }

    @PostMapping("/episodes/{number}/seen")
    public ApiResponse<Void> markMessagesSeen(@PathVariable("number") int episodeNumber,
                                               @Valid @RequestBody MarkSeenHttpRequest request) {
        Long memberId = SecurityUtils.getCurrentUserId();
        orchestrationService.markMessagesSeen(memberId, episodeNumber, request.sequence());
        return ApiResponse.okMessage("진행도가 저장되었습니다.");
    }

    @GetMapping("/report")
    public ApiResponse<AiReportView> getReport() {
        Long memberId = SecurityUtils.getCurrentUserId();
        return ApiResponse.ok(orchestrationService.getReport(memberId));
    }
}
