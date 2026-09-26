package com.luvin.ai.service;

import com.luvin.ai.dto.AiCharacterProfileRequest;
import com.luvin.ai.dto.AiEpisodeMessagesView;
import com.luvin.ai.dto.AiEpisodeProgressView;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.AiRerollView;
import com.luvin.ai.dto.AiSeasonStatusView;
import com.luvin.ai.dto.AiSelectionView;

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

    /** 재시작/새로고침 후 최신 시즌 상태 복구. AI GET season으로 revision/currentEpisode를 동기화한다. */
    AiSeasonStatusView getStatus(Long memberId);

    /**
     * episodeNumber 화 generation을 접수한다. 이미 접수된 job이 있으면 새로 요청하지 않고 기존 진행 상태를
     * 반환한다 (중복 generation 방지, 요구사항 4절).
     */
    AiEpisodeProgressView requestGeneration(Long memberId, int episodeNumber);

    /** 1화 사용자 선택. partnerId는 현재 시즌의 candidate 중 하나여야 한다. */
    AiSelectionView submitEpisode1Selection(Long memberId, UUID partnerId);

    /**
     * 3화 미니게임 결과. 성공 여부와 성공 대상은 이 메서드를 호출하기 전에 Spring이 이미 서버 측에서
     * 판정을 마친 상태여야 한다 (요구사항 5.4) — 이 메서드는 그 판정 결과를 신뢰하고 그대로 AI에 전달한다.
     * 실패면 verifiedPartnerId는 null이어야 한다.
     */
    AiSelectionView submitEpisode3MinigameResult(Long memberId, boolean success, UUID verifiedPartnerId);

    /** 2·4화 1:1 상대 변경. 다음 화 generation이 이미 접수됐으면 거부한다. */
    AiRerollView requestReroll(Long memberId, int episodeNumber, UUID requestedPartnerId);

    /**
     * 해당 회차의 가장 최근 reroll 요청 상태를 조회한다(폴링용). POST 응답 이후 완료 여부를 확인할
     * 별도 수단이 없던 문제를 해결한다. 한 번도 reroll을 요청한 적이 없으면 404.
     */
    AiRerollView getRerollStatus(Long memberId, int episodeNumber);

    /** episodeNumber 화 완성 메시지 pagination 조회. version_id를 고정해서 조회한다. */
    AiEpisodeMessagesView getEpisodeMessages(Long memberId, int episodeNumber, Integer afterSequence, Integer limit);

    /** 앱이 실제로 표시한 마지막 sequence를 저장한다. 조회 자체는 진행도를 바꾸지 않는다 (요구사항 5.6). */
    void markMessagesSeen(Long memberId, int episodeNumber, int sequence);

    /** 5화 완료 후에만 호출 가능한 최종 리포트 조회. */
    AiReportView getReport(Long memberId);
}
