package com.luvin.ai.service;

import com.luvin.ai.client.dto.SeasonResponseDto;
import com.luvin.ai.client.dto.SelectionResponseDto;
import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiEpisodeFeedback;
import com.luvin.ai.domain.AiEpisodeMessageLike;
import com.luvin.ai.domain.AiEpisodeProgress;
import com.luvin.ai.domain.AiGameResult;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonCharacter;
import com.luvin.ai.domain.AiSeasonCreationInput;
import com.luvin.ai.domain.AiSelection;
import com.luvin.ai.domain.AiSelectionSource;
import com.luvin.ai.domain.PartnerLetter;
import com.luvin.ai.repository.AiEpisodeFeedbackRepository;
import com.luvin.ai.repository.AiEpisodeMessageLikeRepository;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonCreationInputRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.repository.AiSelectionRepository;
import com.luvin.ai.repository.PartnerLetterRepository;
import com.luvin.ai.service.exception.AiFeedbackAlreadySubmittedException;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import com.luvin.ai.service.exception.PartnerLetterNotFoundException;
import com.luvin.survey.domain.SurveyResultV2;
import com.luvin.survey.repository.SurveyResultV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final AiSeasonCreationInputRepository seasonCreationInputRepository;
    private final SurveyResultV2Repository surveyResultV2Repository;
    private final PartnerLetterRepository partnerLetterRepository;
    private final AiEpisodeFeedbackRepository episodeFeedbackRepository;
    private final AiEpisodeMessageLikeRepository episodeMessageLikeRepository;

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

    /**
     * 설문 기반 시즌 생성(첫 시즌·다음 시즌 공통). 생성은 무료다(토큰은 4~10화를 열 때 쓴다). 같은 번호의 시즌이 이미
     * 있으면(동시에 두 요청이 같은 생성 입력으로 AI 시즌을 받은 경우) 먼저 저장된 시즌을 돌려준다 — (member_id,
     * season_number) unique라 두 번째 저장은 실패하고, 진 쪽은 이긴 쪽의 시즌을 쓴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiSeason persistNewSeasonFromSurvey(Long memberId, int seasonNumber, SeasonResponseDto response,
                                               UUID surveyResultId) {
        AiSeason raceExisting = seasonRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber).orElse(null);
        if (raceExisting != null) {
            return raceExisting;
        }
        AiSeason season;
        try {
            season = seasonRepository.saveAndFlush(new AiSeason(
                    memberId, seasonNumber, response.seasonId(), response.revision(), response.currentEpisode(),
                    surveyResultId));
        } catch (DataIntegrityViolationException raceLoser) {
            throw new AiSeasonCreationRaceException(raceLoser);
        }
        saveCharacters(season, response);
        return season;
    }

    /** 같은 번호의 시즌을 동시에 저장하다 진 쪽. 호출부가 새 트랜잭션에서 이긴 쪽 시즌을 다시 읽는다. */
    public static final class AiSeasonCreationRaceException extends RuntimeException {
        AiSeasonCreationRaceException(Throwable cause) {
            super(cause);
        }
    }

    @Transactional(readOnly = true)
    public Optional<AiSeason> findSeason(Long memberId, int seasonNumber) {
        return seasonRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber);
    }

    private void saveCharacters(AiSeason season, SeasonResponseDto response) {
        response.characters().forEach(c -> seasonCharacterRepository.save(new AiSeasonCharacter(
                season,
                c.id(),
                AiCharacterRole.fromWire(c.role(), null),
                c.gender())));
    }

    /**
     * 이미 저장된 생성 입력이 있으면 그대로 반환하고(재설문 후에도 payload 고정), 없으면 새로 저장한다.
     * 원격 AI 호출 "이전에" 커밋되어야 하므로 orchestration 서비스가 이 메서드를 먼저 호출한 뒤
     * 반환값을 그대로 재시도에도 재사용한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiSeasonCreationInput persistPendingCreationInput(Long memberId, int seasonNumber, UUID surveyResultId,
                                                               String profileJson, String requestHash,
                                                               String clientRequestId) {
        // (member_id, season_number) unique가 사용자 단위 락 역할을 한다 — 동시에 두 요청이 와도 생성 입력(=AI
        // idempotency key)은 하나만 남고, 진 쪽은 이긴 쪽 입력으로 같은 AI 시즌을 받는다.
        Optional<AiSeasonCreationInput> existing =
                seasonCreationInputRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber);
        if (existing.isPresent()) {
            return existing.get();
        }
        SurveyResultV2 surveyResult = surveyResultV2Repository.getReferenceById(surveyResultId);
        try {
            return seasonCreationInputRepository.saveAndFlush(new AiSeasonCreationInput(
                    memberId, seasonNumber, surveyResult, profileJson, requestHash, UUID.randomUUID(),
                    clientRequestId));
        } catch (DataIntegrityViolationException raceLoser) {
            throw new AiSeasonCreationRaceException(raceLoser);
        }
    }

    @Transactional
    public void markCreationInputSucceeded(Long creationInputId) {
        seasonCreationInputRepository.findById(creationInputId).ifPresent(AiSeasonCreationInput::markSucceeded);
    }

    @Transactional(readOnly = true)
    public Optional<AiSeasonCreationInput> findCreationInput(Long memberId, int seasonNumber) {
        return seasonCreationInputRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber);
    }

    @Transactional(readOnly = true)
    public Optional<AiSeason> findCurrentSeason(Long memberId) {
        return seasonRepository.findFirstByMemberIdOrderBySeasonNumberDesc(memberId);
    }

    /** 최신 시즌부터(seasonNumber 내림차순). */
    @Transactional(readOnly = true)
    public List<AiSeason> findAllSeasons(Long memberId) {
        return seasonRepository.findAllByMemberIdOrderBySeasonNumberDesc(memberId);
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

    private AiSeason getSeasonOrThrow(Long seasonPk) {
        return seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
    }

    /**
     * EP5 generation 성공 직후(즉시 트리거) 또는 실패 재시도 sweep에서 호출된다. 시즌당 1개 row만
     * 만든다(season_id unique) — 이미 있으면 그대로 반환한다. 두 경로가 거의 동시에 처음 호출되는
     * 경쟁 상황을 대비해 REQUIRES_NEW로 격리하고, saveAndFlush로 유니크 제약 위반을 즉시 이 짧은
     * 트랜잭션 안에서만 터지게 한 뒤(격리하지 않으면 Postgres가 트랜잭션을 abort 상태로 만들어
     * 이어지는 조회까지 실패한다 — AiIdempotencyKeyService.issueNew()와 동일한 이유), 진 쪽은 이긴
     * 쪽이 만든 row를 그대로 재사용한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PartnerLetter ensureLetterRow(Long seasonPk, Long memberId) {
        return partnerLetterRepository.findBySeason_IdAndEpisodeOrderIsNull(seasonPk).orElseGet(() -> {
            try {
                return partnerLetterRepository.saveAndFlush(
                        new PartnerLetter(memberId, seasonRepository.getReferenceById(seasonPk)));
            } catch (DataIntegrityViolationException raceLoser) {
                return partnerLetterRepository.findBySeason_IdAndEpisodeOrderIsNull(seasonPk).orElseThrow(() -> raceLoser);
            }
        });
    }

    @Transactional
    public void persistLetterJobAccepted(Long letterPk, UUID jobId) {
        partnerLetterRepository.findById(letterPk).ifPresent(letter -> letter.markJobAccepted(jobId));
    }

    /** POST /letter가 409 LETTER_ALREADY_EXISTS를 반환했을 때, GET /letter로 받아온 결과를 그대로 반영한다. */
    @Transactional
    public void markLetterSucceeded(Long letterPk, UUID partnerId, String message, String renderMode) {
        partnerLetterRepository.findById(letterPk)
                .ifPresent(letter -> letter.markSucceeded(partnerId, message, renderMode));
    }

    /** 그 시즌(최신 시즌)의 안 읽은 쪽지 중 가장 최근 것. 지난 시즌에 남은 안 읽은 쪽지는 새 시즌에 띄우지 않는다. */
    @Transactional(readOnly = true)
    public Optional<PartnerLetter> findUnreadLetter(Long memberId, Long seasonPk) {
        return partnerLetterRepository.findUnreadOrderByCreatedAtDesc(memberId).stream()
                .filter(letter -> letter.getSeason().getId().equals(seasonPk))
                .findFirst();
    }

    @Transactional
    public void markLetterRead(Long letterId, Long memberId) {
        PartnerLetter letter = partnerLetterRepository.findByIdAndMemberId(letterId, memberId)
                .orElseThrow(PartnerLetterNotFoundException::new);
        letter.markRead();
    }

    @Transactional(readOnly = true)
    public Optional<AiEpisodeFeedback> findFeedback(Long seasonPk, int episodeNumber) {
        return episodeFeedbackRepository.findBySeason_IdAndEpisodeNumber(seasonPk, episodeNumber);
    }

    /**
     * 회차당 1회 제약(season_id + episode_number unique)을 DB에서도 강제한다 — 호출 전에 이미
     * findFeedback으로 선제 확인하지만, 동시에 두 번 제출되는 경쟁 상황은 유니크 제약 위반을
     * AiFeedbackAlreadySubmittedException으로 변환해 똑같이 409로 응답한다.
     */
    @Transactional
    public AiEpisodeFeedback persistEpisodeFeedback(Long seasonPk, int episodeNumber, UUID messageId, String content) {
        try {
            return episodeFeedbackRepository.save(new AiEpisodeFeedback(
                    seasonRepository.getReferenceById(seasonPk), episodeNumber, messageId, content));
        } catch (DataIntegrityViolationException raceLoser) {
            throw new AiFeedbackAlreadySubmittedException();
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findLikedMessageIds(Long seasonPk, int episodeNumber) {
        return episodeMessageLikeRepository.findBySeason_IdAndEpisodeNumber(seasonPk, episodeNumber).stream()
                .map(AiEpisodeMessageLike::getMessageId)
                .toList();
    }

    /** row 존재 여부가 곧 liked 상태다 — 있으면 지우고(끔), 없으면 만든다(켬). 반환값은 토글 후 상태. */
    @Transactional
    public boolean toggleEpisodeMessageLike(Long seasonPk, int episodeNumber, UUID messageId) {
        Optional<AiEpisodeMessageLike> existing = episodeMessageLikeRepository
                .findBySeason_IdAndEpisodeNumberAndMessageId(seasonPk, episodeNumber, messageId);
        if (existing.isPresent()) {
            episodeMessageLikeRepository.delete(existing.get());
            return false;
        }
        episodeMessageLikeRepository.save(new AiEpisodeMessageLike(
                seasonRepository.getReferenceById(seasonPk), episodeNumber, messageId));
        return true;
    }
}
