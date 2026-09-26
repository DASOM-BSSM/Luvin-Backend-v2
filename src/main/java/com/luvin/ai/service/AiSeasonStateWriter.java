package com.luvin.ai.service;

import com.luvin.ai.client.dto.SeasonResponseDto;
import com.luvin.ai.client.dto.SelectionResponseDto;
import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiEpisodeProgress;
import com.luvin.ai.domain.AiGameResult;
import com.luvin.ai.domain.AiRerollRequest;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonCharacter;
import com.luvin.ai.domain.AiSeasonCreationInput;
import com.luvin.ai.domain.AiSelection;
import com.luvin.ai.domain.AiSelectionSource;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
import com.luvin.ai.repository.AiRerollRequestRepository;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonCreationInputRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.repository.AiSelectionRepository;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import com.luvin.survey.domain.SurveyResultV2;
import com.luvin.survey.repository.SurveyResultV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AI 호출 결과를 영속화하는 모든 짧은 write transaction을 이 컴포넌트에 모아둔다.
 *
 * 의도적으로 {@link AiSeasonOrchestrationServiceImpl}과 분리했다: 같은 클래스 안에서 this.xxx()로
 * @Transactional 메서드를 호출하면 Spring AOP 프록시가 가로채지 못해(self-invocation) 트랜잭션이
 * 실제로 시작되지 않는다. orchestration 서비스가 이 빈을 주입받아 호출하면 프록시를 정상적으로 거친다.
 *
 * season 등 상위 entity는 detached 상태로 넘기지 않고 PK(Long)만 받아 트랜잭션 내부에서
 * getReferenceById로 다시 조회한다. 그래야 Hibernate가 detached 참조 관련 오류 없이
 * 안전하게 연관관계를 구성할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class AiSeasonStateWriter {

    private final AiSeasonRepository seasonRepository;
    private final AiSeasonCharacterRepository seasonCharacterRepository;
    private final AiEpisodeProgressRepository episodeProgressRepository;
    private final AiSelectionRepository selectionRepository;
    private final AiRerollRequestRepository rerollRequestRepository;
    private final AiSeasonCreationInputRepository seasonCreationInputRepository;
    private final SurveyResultV2Repository surveyResultV2Repository;

    @Transactional
    public AiSeason persistNewSeason(Long memberId, SeasonResponseDto response) {
        return persistNewSeason(memberId, response, null);
    }

    @Transactional
    public AiSeason persistNewSeasonFromSurvey(Long memberId, SeasonResponseDto response, UUID surveyResultId) {
        return persistNewSeason(memberId, response, surveyResultId);
    }

    private AiSeason persistNewSeason(Long memberId, SeasonResponseDto response, UUID surveyResultId) {
        AiSeason raceExisting = seasonRepository.findByMemberId(memberId).orElse(null);
        if (raceExisting != null) {
            return raceExisting;
        }

        AiSeason season = seasonRepository.save(new AiSeason(
                memberId, response.seasonId(), response.revision(), response.currentEpisode(), surveyResultId));

        response.characters().forEach(c -> seasonCharacterRepository.save(new AiSeasonCharacter(
                season,
                c.id(),
                AiCharacterRole.fromWire(c.role(), null),
                c.gender())));

        return season;
    }

    /**
     * 이미 저장된 생성 입력이 있으면 그대로 반환하고(재설문 후에도 payload 고정), 없으면 새로 저장한다.
     * 원격 AI 호출 "이전에" 커밋되어야 하므로 orchestration 서비스가 이 메서드를 먼저 호출한 뒤
     * 반환값을 그대로 재시도에도 재사용한다.
     */
    @Transactional
    public AiSeasonCreationInput persistPendingCreationInput(Long memberId, UUID surveyResultId,
                                                               String profileJson, String requestHash) {
        return seasonCreationInputRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    SurveyResultV2 surveyResult = surveyResultV2Repository.getReferenceById(surveyResultId);
                    return seasonCreationInputRepository.save(new AiSeasonCreationInput(
                            memberId, surveyResult, profileJson, requestHash, UUID.randomUUID()));
                });
    }

    @Transactional
    public void markCreationInputSucceeded(Long creationInputId) {
        seasonCreationInputRepository.findById(creationInputId).ifPresent(AiSeasonCreationInput::markSucceeded);
    }

    @Transactional(readOnly = true)
    public Optional<AiSeasonCreationInput> findCreationInput(Long memberId) {
        return seasonCreationInputRepository.findByMemberId(memberId);
    }

    @Transactional
    public AiSeason syncSeason(Long seasonPk, Integer revision, Integer currentEpisode) {
        AiSeason season = getSeasonOrThrow(seasonPk);
        season.syncFromAi(revision, currentEpisode);
        return season;
    }

    @Transactional
    public AiEpisodeProgress persistGenerationAccepted(Long seasonPk, int episodeNumber, UUID jobId) {
        AiEpisodeProgress progress = episodeProgressRepository
                .findBySeason_IdAndEpisodeNumber(seasonPk, episodeNumber)
                .orElseGet(() -> new AiEpisodeProgress(seasonRepository.getReferenceById(seasonPk), episodeNumber, jobId));
        return episodeProgressRepository.save(progress);
    }

    @Transactional
    public AiSelection persistSelection(Long seasonPk, int episodeNumber, AiSelectionSource source,
                                         UUID sourceEventId, AiGameResult gameResult, SelectionResponseDto response) {
        AiSelection selection = new AiSelection(seasonRepository.getReferenceById(seasonPk), episodeNumber, source,
                sourceEventId, gameResult, response.selectedPartnerId());
        selection.applyAiResult(response.selectionId(), response.selectedPartnerId(), response.revision());
        AiSelection saved = selectionRepository.save(selection);

        AiSeason season = getSeasonOrThrow(seasonPk);
        season.syncFromAi(response.revision(), season.getCurrentEpisode());
        return saved;
    }

    @Transactional
    public AiRerollRequest persistRerollAccepted(Long seasonPk, int episodeNumber, UUID previousVersionId,
                                                  UUID requestedPartnerId, UUID idempotencyKey, UUID jobId) {
        AiRerollRequest rerollRequest = rerollRequestRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> new AiRerollRequest(seasonRepository.getReferenceById(seasonPk),
                        episodeNumber, idempotencyKey, previousVersionId, requestedPartnerId, jobId));
        return rerollRequestRepository.save(rerollRequest);
    }

    @Transactional
    public void persistVersionReplaced(Long episodeProgressPk, UUID newVersionId) {
        episodeProgressRepository.findById(episodeProgressPk)
                .ifPresent(progress -> progress.replaceVersion(newVersionId));
    }

    @Transactional
    public void markMessagesSeen(Long seasonPk, int episodeNumber, int sequence) {
        episodeProgressRepository.findBySeason_IdAndEpisodeNumber(seasonPk, episodeNumber)
                .ifPresent(progress -> progress.advanceLastSeenSequence(sequence));
    }

    @Transactional
    public void markSeasonCompleted(Long seasonPk) {
        seasonRepository.findById(seasonPk).ifPresent(AiSeason::complete);
    }

    @Transactional(readOnly = true)
    public List<AiSeasonCharacter> findCharacters(Long seasonPk) {
        return seasonCharacterRepository.findAllBySeason_Id(seasonPk);
    }

    @Transactional(readOnly = true)
    public Optional<AiRerollRequest> findLatestReroll(Long seasonPk, int episodeNumber) {
        return rerollRequestRepository.findFirstBySeason_IdAndEpisodeNumberOrderByCreatedAtDesc(seasonPk, episodeNumber);
    }

    private AiSeason getSeasonOrThrow(Long seasonPk) {
        return seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
    }
}
