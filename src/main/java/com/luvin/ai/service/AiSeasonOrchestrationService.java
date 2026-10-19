package com.luvin.ai.service;

import com.luvin.ai.dto.AiCharacterProfileRequest;
import com.luvin.ai.dto.AiEpisodeFeedbackView;
import com.luvin.ai.dto.AiEpisodeMessagesView;
import com.luvin.ai.dto.AiEpisodeProgressView;
import com.luvin.ai.dto.AiMessageLikeView;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.AiRerollView;
import com.luvin.ai.dto.AiSeasonStatusView;
import com.luvin.ai.dto.AiSeasonSummaryView;
import com.luvin.ai.dto.AiSelectionView;
import com.luvin.ai.dto.PartnerLetterView;
import com.luvin.ai.dto.StartNextSeasonRequest;

import java.util.List;
import java.util.UUID;

/**
 * openapi.json 8개 endpoint에 대응하는 Spring 쪽 application service.
 * 시즌 생성부터 1~5화 진행, reroll, report까지 담당한다 (요구사항 2절, 5절).
 */
public interface AiSeasonOrchestrationService {

    /** 이미 시즌이 있으면 그 상태를 그대로 반환한다 (멱등). */
    AiSeasonStatusView createSeason(Long memberId, AiCharacterProfileRequest representative);

    /**
     * 서버가 저장한 설문 v2 결과(gender + 13개 core 점수)로 시즌을 생성한다. 기존 시즌이 있으면 그대로
     * 반환하고, 요청한 surveyResultId가 본인의 최신 결과가 아니면 409(SURVEY_RESULT_STALE)를 던진다.
     */
    AiSeasonStatusView createSeasonFromSurvey(Long memberId, java.util.UUID surveyResultId);

    /**
     * 현재 시즌이 COMPLETED일 때만 다음 시즌을 생성한다. 무료 횟수가 남아있으면 그걸, 없으면 토큰
     * 1개를 시즌 생성과 같은 트랜잭션에서 차감한다. 아직 진행 중인 시즌이 있으면 409(SEASON_IN_PROGRESS),
     * 토큰/무료횟수가 부족하면 409(INSUFFICIENT_TOKENS)를 던진다.
     */
    AiSeasonStatusView startNextSeason(Long memberId, StartNextSeasonRequest request);

    /** 이 사용자의 모든 시즌을 seasonNumber 오름차순으로 반환한다(지난 시즌 포함). */
    List<AiSeasonSummaryView> listSeasons(Long memberId);

    /** 재시작/새로고침 후 최신 시즌 상태 복구. AI GET season으로 revision/currentEpisode를 동기화한다. */
    AiSeasonStatusView getStatus(Long memberId);

    /**
     * episodeNumber 화 generation을 접수한다. 이미 접수된 job이 있으면 새로 요청하지 않고 기존 진행 상태를
     * 반환한다 (중복 generation 방지, 요구사항 4절). 완료된(지난) 시즌에는 요청할 수 없다(409 SEASON_COMPLETED).
     */
    AiEpisodeProgressView requestGeneration(Long memberId, int episodeNumber);

    /**
     * 1화 사용자 선택. partnerId는 현재 시즌의 candidate 중 하나여야 한다.
     * 완료된(지난) 시즌에는 요청할 수 없다(409 SEASON_COMPLETED).
     */
    AiSelectionView submitEpisode1Selection(Long memberId, UUID partnerId);

    /**
     * 3화 미니게임 결과. 성공 여부와 성공 대상은 이 메서드를 호출하기 전에 Spring이 이미 서버 측에서
     * 판정을 마친 상태여야 한다 (요구사항 5.4) — 이 메서드는 그 판정 결과를 신뢰하고 그대로 AI에 전달한다.
     * 실패면 verifiedPartnerId는 null이어야 한다. 완료된(지난) 시즌에는 요청할 수 없다(409 SEASON_COMPLETED).
     */
    AiSelectionView submitEpisode3MinigameResult(Long memberId, boolean success, UUID verifiedPartnerId);

    /**
     * 2·4화 1:1 상대 변경. 다음 화 generation이 이미 접수됐으면 거부한다. 완료된(지난) 시즌에는
     * 요청할 수 없다(409 SEASON_COMPLETED).
     */
    AiRerollView requestReroll(Long memberId, int episodeNumber, UUID requestedPartnerId);

    /**
     * 해당 회차의 가장 최근 reroll 요청 상태를 조회한다(폴링용). POST 응답 이후 완료 여부를 확인할
     * 별도 수단이 없던 문제를 해결한다. 한 번도 reroll을 요청한 적이 없으면 404.
     * seasonId가 null이면 현재(최신) 시즌, 지정하면 본인 소유의 그 시즌(지난 시즌 포함)을 조회한다.
     */
    AiRerollView getRerollStatus(Long memberId, UUID seasonId, int episodeNumber);

    /**
     * episodeNumber 화 완성 메시지 pagination 조회. version_id를 고정해서 조회한다.
     * seasonId가 null이면 현재(최신) 시즌, 지정하면 본인 소유의 그 시즌(지난 시즌 포함)을 조회한다.
     */
    AiEpisodeMessagesView getEpisodeMessages(
            Long memberId, UUID seasonId, int episodeNumber, Integer afterSequence, Integer limit);

    /**
     * 앱이 실제로 표시한 마지막 sequence를 저장한다. 조회 자체는 진행도를 바꾸지 않는다 (요구사항 5.6).
     * 완료된(지난) 시즌에는 요청할 수 없다(409 SEASON_COMPLETED).
     */
    void markMessagesSeen(Long memberId, int episodeNumber, int sequence);

    /**
     * 5화 완료 후에만 호출 가능한 최종 리포트 조회. seasonId가 null이면 현재(최신) 시즌, 지정하면
     * 본인 소유의 그 시즌(지난 시즌 포함)을 조회한다.
     */
    AiReportView getReport(Long memberId, UUID seasonId);

    /**
     * 안 읽은 "SECRET LOVE" 쪽지를 조회한다(최신 1개만, 여러 개 쌓여도 그중 최신). AI 쪽 생성 job이
     * 아직 SUCCEEDED되지 않았으면 null(쪽지로 보여줄 내용이 없다).
     */
    PartnerLetterView getUnreadLetter(Long memberId);

    /** 쪽지를 읽음 처리한다. 본인 소유가 아니거나 존재하지 않으면 404(PARTNER_LETTER_NOT_FOUND). */
    void markLetterRead(Long memberId, Long letterId);

    /**
     * "나의 빵에게" — 2화/4화 1:1 대화 중 본인 AI 분신(representative)이 한 말 한 줄을 골라 피드백을
     * 남긴다. messageId는 해당 회차의 실제 one_to_one 메시지이고 speaker가 representative여야
     * 한다(그렇지 않으면 400). 회차당 1회만 가능하다(두 번째 시도는 409 FEEDBACK_ALREADY_SUBMITTED).
     * seasonId가 null이면 현재(최신) 시즌, 지정하면 본인 소유의 그 시즌(지난 시즌 포함)을 대상으로 한다.
     */
    AiEpisodeFeedbackView submitEpisodeFeedback(
            Long memberId, UUID seasonId, int episodeNumber, UUID messageId, String content);

    /**
     * EP5 generation이 SUCCEEDED되는 즉시(AiJobPollingScheduler가 호출) "SECRET LOVE" 쪽지 생성을
     * AI 서비스에 접수한다. 멱등: 이미 접수/완료됐으면 아무것도 하지 않고, FAILED 이후 재시도도
     * 같은 메서드로 처리한다(로컬 3회 한도 도달 시 더 이상 시도하지 않음). 실패해도 EP5 성공 처리
     * 자체에는 영향이 없도록 호출부에서 best-effort로 다룬다.
     */
    void ensureLetterGenerationStarted(Long seasonPk);

    /**
     * ep2/ep4 1:1 대화 메시지 하트를 토글한다(켜져 있으면 끄고, 꺼져 있으면 켠다). 본인 분신/상대
     * 메시지 모두 가능하고 개수 제한이 없다. messageId는 해당 회차의 실제 one_to_one 메시지여야
     * 한다(아니면 400). seasonId가 null이면 현재(최신) 시즌, 지정하면 본인 소유의 그 시즌(지난
     * 시즌 포함)을 대상으로 한다.
     */
    AiMessageLikeView toggleEpisodeMessageLike(Long memberId, UUID seasonId, int episodeNumber, UUID messageId);
}
