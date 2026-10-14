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
import com.luvin.ai.service.exception.AiInsufficientTokensException;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import com.luvin.survey.domain.SurveyResultV2;
import com.luvin.survey.repository.SurveyResultV2Repository;
import com.luvin.token.domain.TokenHistory;
import com.luvin.token.domain.TokenHistoryType;
import com.luvin.token.domain.UserToken;
import com.luvin.token.repository.TokenHistoryRepository;
import com.luvin.token.repository.UserTokenRepository;
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
    private final UserTokenRepository userTokenRepository;
    private final TokenHistoryRepository tokenHistoryRepository;

    private static final int FIRST_SEASON_NUMBER = 1;

    /** 레거시 원시-traits 경로(내부 테스트 fixture 용) — 무료 횟수/토큰을 소비하지 않는다. */
    @Transactional
    public AiSeason persistNewSeason(Long memberId, SeasonResponseDto response) {
        AiSeason raceExisting = seasonRepository.findFirstByMemberIdOrderBySeasonNumberDesc(memberId).orElse(null);
        if (raceExisting != null) {
            return raceExisting;
        }
        AiSeason season = seasonRepository.save(new AiSeason(
                memberId, FIRST_SEASON_NUMBER, response.seasonId(), response.revision(), response.currentEpisode(), null));
        saveCharacters(season, response);
        return season;
    }

    /** 설문 기반 첫 시즌 생성. 신규 유저는 무료 횟수(1)로 시작하므로 항상 무료로 소진된다. */
    @Transactional
    public AiSeason persistNewSeasonFromSurvey(Long memberId, SeasonResponseDto response, UUID surveyResultId) {
        AiSeason raceExisting = seasonRepository.findFirstByMemberIdOrderBySeasonNumberDesc(memberId).orElse(null);
        if (raceExisting != null) {
            return raceExisting;
        }
        AiSeason season = seasonRepository.save(new AiSeason(
                memberId, FIRST_SEASON_NUMBER, response.seasonId(), response.revision(), response.currentEpisode(),
                surveyResultId));
        consumeOneSeasonCredit(memberId);
        saveCharacters(season, response);
        return season;
    }

    /**
     * 2번째 이후 시즌 생성. 무료 횟수가 남아있으면 그걸, 없으면 토큰 1개를 시즌 생성과 같은 트랜잭션에서
     * 차감한다 — 토큰 차감만 실패하거나 시즌만 생성되는 경우가 없다.
     */
    @Transactional
    public AiSeason persistNextSeason(Long memberId, int seasonNumber, SeasonResponseDto response, UUID surveyResultId) {
        AiSeason raceExisting = seasonRepository.findFirstByMemberIdOrderBySeasonNumberDesc(memberId).orElse(null);
        if (raceExisting != null && raceExisting.getSeasonNumber() >= seasonNumber) {
            // 이미 이 번호(또는 더 최신) 시즌이 만들어져 있다 — 재시도/경쟁에서 진 쪽이거나 중복 요청.
            return raceExisting;
        }
        AiSeason season = seasonRepository.save(new AiSeason(
                memberId, seasonNumber, response.seasonId(), response.revision(), response.currentEpisode(),
                surveyResultId));
        consumeOneSeasonCredit(memberId);
        saveCharacters(season, response);
        return season;
    }

    private void saveCharacters(AiSeason season, SeasonResponseDto response) {
        response.characters().forEach(c -> seasonCharacterRepository.save(new AiSeasonCharacter(
                season,
                c.id(),
                AiCharacterRole.fromWire(c.role(), null),
                c.gender())));
    }

    private void consumeOneSeasonCredit(Long memberId) {
        UserToken userToken = userTokenRepository.findByMemberId(memberId)
                .orElseGet(() -> userTokenRepository.save(new UserToken(memberId, 0, 1)));
        if (userToken.getFreeSimulationCount() > 0) {
            userToken.consumeFreeSimulation();
            return;
        }
        if (userToken.getTokenBalance() < 1) {
            throw new AiInsufficientTokensException(1, userToken.getTokenBalance());
        }
        userToken.use(1);
        tokenHistoryRepository.save(new TokenHistory(memberId, TokenHistoryType.USE, -1, "러빈지옥 시즌 시작"));
    }

    /**
     * 이미 저장된 생성 입력이 있으면 그대로 반환하고(재설문 후에도 payload 고정), 없으면 새로 저장한다.
     * 원격 AI 호출 "이전에" 커밋되어야 하므로 orchestration 서비스가 이 메서드를 먼저 호출한 뒤
     * 반환값을 그대로 재시도에도 재사용한다.
     */
    @Transactional
    public AiSeasonCreationInput persistPendingCreationInput(Long memberId, UUID surveyResultId,
                                                               String profileJson, String requestHash) {
        return seasonCreationInputRepository.findByMemberIdAndSeasonNumber(memberId, FIRST_SEASON_NUMBER)
                .orElseGet(() -> {
                    SurveyResultV2 surveyResult = surveyResultV2Repository.getReferenceById(surveyResultId);
                    return seasonCreationInputRepository.save(new AiSeasonCreationInput(
                            memberId, FIRST_SEASON_NUMBER, surveyResult, profileJson, requestHash, UUID.randomUUID()));
                });
    }

    /** persistPendingCreationInput과 같은 패턴을 2번째 이후 시즌(seasonNumber)에 대해 적용한다. */
    @Transactional
    public AiSeasonCreationInput persistPendingNextCreationInput(Long memberId, int seasonNumber, UUID surveyResultId,
                                                                   String profileJson, String requestHash) {
        return seasonCreationInputRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber)
                .orElseGet(() -> {
                    SurveyResultV2 surveyResult = surveyResultV2Repository.getReferenceById(surveyResultId);
                    return seasonCreationInputRepository.save(new AiSeasonCreationInput(
                            memberId, seasonNumber, surveyResult, profileJson, requestHash, UUID.randomUUID()));
                });
    }

    @Transactional
    public void markCreationInputSucceeded(Long creationInputId) {
        seasonCreationInputRepository.findById(creationInputId).ifPresent(AiSeasonCreationInput::markSucceeded);
    }

    @Transactional(readOnly = true)
    public Optional<AiSeasonCreationInput> findCreationInput(Long memberId) {
        return seasonCreationInputRepository.findByMemberIdAndSeasonNumber(memberId, FIRST_SEASON_NUMBER);
    }

    @Transactional(readOnly = true)
    public Optional<AiSeasonCreationInput> findNextCreationInput(Long memberId, int seasonNumber) {
        return seasonCreationInputRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber);
    }

    @Transactional(readOnly = true)
    public Optional<AiSeason> findCurrentSeason(Long memberId) {
        return seasonRepository.findFirstByMemberIdOrderBySeasonNumberDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<AiSeason> findAllSeasons(Long memberId) {
        return seasonRepository.findAllByMemberIdOrderBySeasonNumberAsc(memberId);
    }

    @Transactional
    public AiSeason syncSeason(Long seasonPk, Integer revision, Integer currentEpisode) {
        AiSeason season = getSeasonOrThrow(seasonPk);
        season.syncFromAi(revision, currentEpisode);
        return season;
    }

    @Transactional
    public AiEpisodeProgress persistGenerationAccepted(Long seasonPk, int episodeNumber, UUID jobId) {
        AiEpisodeProgress existing = episodeProgressRepository
                .findBySeason_IdAndEpisodeNumber(seasonPk, episodeNumber).orElse(null);
        if (existing == null) {
            return episodeProgressRepository.save(
                    new AiEpisodeProgress(seasonRepository.getReferenceById(seasonPk), episodeNumber, jobId));
        }
        // 기존 row가 있다면 이전 job이 FAILED였다가 재요청으로 새 job을 받은 경우다(그 외에는
        // requestGeneration의 isGenerationAccepted 가드에서 이미 걸러진다) — 상태를 새 job 기준으로
        // 리셋한다. orElseGet만 쓰면 새 jobId가 조용히 버려지고 기존 FAILED row가 그대로 남는 버그가 있었다.
        existing.resetForRetry(jobId);
        return episodeProgressRepository.save(existing);
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
