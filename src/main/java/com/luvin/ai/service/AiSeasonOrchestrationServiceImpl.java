package com.luvin.ai.service;

import com.luvin.ai.client.AiServiceClient;
import com.luvin.ai.client.dto.CharacterProfileDto;
import com.luvin.ai.client.dto.CreateSeasonRequestDto;
import com.luvin.ai.client.dto.EpisodeResponseDto;
import com.luvin.ai.client.dto.GenerationRequestDto;
import com.luvin.ai.client.dto.JobResponseDto;
import com.luvin.ai.client.dto.MessageResponseDto;
import com.luvin.ai.client.dto.ReportResponseDto;
import com.luvin.ai.client.dto.RerollRequestDto;
import com.luvin.ai.client.dto.SeasonResponseDto;
import com.luvin.ai.client.dto.SelectionRequestDto;
import com.luvin.ai.client.dto.SelectionResponseDto;
import com.luvin.ai.client.dto.TraitsDto;
import com.luvin.ai.client.exception.AiRevisionConflictException;
import com.luvin.ai.client.exception.AiVersionSupersededException;
import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiEpisodeProgress;
import com.luvin.ai.domain.AiGameResult;
import com.luvin.ai.domain.AiIdempotencyActionType;
import com.luvin.ai.domain.AiJobStatus;
import com.luvin.ai.domain.AiRerollRequest;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonCharacter;
import com.luvin.ai.domain.AiSeasonCreationInput;
import com.luvin.ai.domain.AiSelection;
import com.luvin.ai.domain.AiSelectionSource;
import com.luvin.ai.dto.AiCharacterProfileRequest;
import com.luvin.ai.dto.AiCharacterView;
import com.luvin.ai.dto.AiTraitsRequest;
import com.luvin.ai.dto.AiEpisodeMessagesView;
import com.luvin.ai.dto.AiEpisodeProgressView;
import com.luvin.ai.dto.AiMessageView;
import com.luvin.ai.dto.AiSceneKind;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.AiRerollView;
import com.luvin.ai.dto.AiSeasonStatusView;
import com.luvin.ai.dto.AiSelectionView;
import com.luvin.ai.dto.AiTopicView;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.repository.AiSelectionRepository;
import com.luvin.ai.security.OwnerSubjectProvider;
import com.luvin.ai.service.exception.AiCandidateInvalidException;
import com.luvin.ai.service.exception.AiEpisodeProgressionException;
import com.luvin.ai.service.exception.AiInputValidationException;
import com.luvin.ai.service.exception.AiRerollNotAllowedException;
import com.luvin.ai.service.exception.AiRerollNotFoundException;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import com.luvin.survey.domain.SurveyResultV2;
import com.luvin.survey.service.exception.ProfileRequiredException;
import com.luvin.survey.service.exception.SurveyResultNotFoundException;
import com.luvin.survey.service.exception.SurveyResultStaleException;
import com.luvin.user.domain.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AiSeasonOrchestrationService 구현체.
 *
 * 설계 원칙(요구사항 4절): 외부 HTTP 호출은 이 클래스 안에서 수행하고, 그 결과를 반영하는 로컬 상태 변경은
 * 전부 {@link AiSeasonStateWriter}(별도 스프링 빈)에 위임한다. 같은 클래스 안에서 this.xxx()로
 * @Transactional 메서드를 호출하면 AOP 프록시가 가로채지 못하는 self-invocation 문제가 있기 때문에,
 * 이 클래스 자체에는 @Transactional 메서드를 두지 않는다.
 */
@Service
public class AiSeasonOrchestrationServiceImpl implements AiSeasonOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AiSeasonOrchestrationServiceImpl.class);
    private static final int EPISODE_1 = 1;
    private static final int EPISODE_3 = 3;
    private static final Set<Integer> REROLLABLE_EPISODES = Set.of(2, 4);
    private static final int FINAL_EPISODE = 5;

    private final AiServiceClient aiServiceClient;
    private final AiInputValidator inputValidator;
    private final OwnerSubjectProvider ownerSubjectProvider;
    private final AiIdempotencyKeyService idempotencyKeyService;
    private final AiSeasonStateWriter stateWriter;
    private final AiSeasonRepository seasonRepository;
    private final AiSeasonCharacterRepository seasonCharacterRepository;
    private final AiEpisodeProgressRepository episodeProgressRepository;
    private final AiSelectionRepository selectionRepository;
    private final AiTraitsWireAdapter traitsWireAdapter;
    private final com.luvin.user.repository.UserRepository userRepository;
    private final com.luvin.survey.repository.SurveyResultV2Repository surveyResultV2Repository;

    public AiSeasonOrchestrationServiceImpl(AiServiceClient aiServiceClient,
                                             AiInputValidator inputValidator,
                                             OwnerSubjectProvider ownerSubjectProvider,
                                             AiIdempotencyKeyService idempotencyKeyService,
                                             AiSeasonStateWriter stateWriter,
                                             AiSeasonRepository seasonRepository,
                                             AiSeasonCharacterRepository seasonCharacterRepository,
                                             AiEpisodeProgressRepository episodeProgressRepository,
                                             AiSelectionRepository selectionRepository,
                                             AiTraitsWireAdapter traitsWireAdapter,
                                             com.luvin.user.repository.UserRepository userRepository,
                                             com.luvin.survey.repository.SurveyResultV2Repository surveyResultV2Repository) {
        this.aiServiceClient = aiServiceClient;
        this.inputValidator = inputValidator;
        this.ownerSubjectProvider = ownerSubjectProvider;
        this.idempotencyKeyService = idempotencyKeyService;
        this.stateWriter = stateWriter;
        this.seasonRepository = seasonRepository;
        this.seasonCharacterRepository = seasonCharacterRepository;
        this.episodeProgressRepository = episodeProgressRepository;
        this.selectionRepository = selectionRepository;
        this.traitsWireAdapter = traitsWireAdapter;
        this.userRepository = userRepository;
        this.surveyResultV2Repository = surveyResultV2Repository;
    }

    // ---------------------------------------------------------------- 시즌 생성

    @Override
    public AiSeasonStatusView createSeason(Long memberId, AiCharacterProfileRequest representative) {
        AiSeason existing = seasonRepository.findByMemberId(memberId).orElse(null);
        if (existing != null) {
            // 멱등: 이미 시즌이 있으면 새로 만들지 않고 기존 상태를 그대로 반환한다.
            log.info("season already exists for member, returning existing status");
            return toStatusView(existing, stateWriter.findCharacters(existing.getId()));
        }

        inputValidator.validateCharacterProfile(representative);

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        UUID idempotencyKey = idempotencyKeyService.resolveOrIssue(
                memberId, AiIdempotencyActionType.SEASON_CREATE, "season:create");

        CreateSeasonRequestDto request = CreateSeasonRequestDto.of(toClientProfile(representative));
        SeasonResponseDto response = aiServiceClient.createSeason(ownerSubject, idempotencyKey, request);

        AiSeason season = stateWriter.persistNewSeason(memberId, response);
        return toStatusView(season, stateWriter.findCharacters(season.getId()));
    }

    @Override
    public AiSeasonStatusView createSeasonFromSurvey(Long memberId, UUID surveyResultId) {
        AiSeason existing = seasonRepository.findByMemberId(memberId).orElse(null);
        if (existing != null) {
            // 멱등: 기존 시즌이 있으면 재설문 결과로 바꾸지 않고 그대로 반환한다.
            return toStatusView(existing, stateWriter.findCharacters(existing.getId()));
        }

        AiSeasonCreationInput creationInput = stateWriter.findCreationInput(memberId).orElse(null);
        if (creationInput == null) {
            User user = userRepository.findById(memberId)
                    .orElseThrow(() -> new com.luvin.common.exception.UserNotFoundException(memberId));
            // 요청한 surveyResultId가 본인의 "현재 최신" 결과가 아니면, 서버가 임의로 최신으로
            // 보정하지 않고 409로 재조회를 요구한다 (요구사항: 서버가 몰래 최신판으로 채점하지 않는다).
            if (user.getLatestSurveyResultId() == null || !user.getLatestSurveyResultId().equals(surveyResultId)) {
                throw new SurveyResultStaleException();
            }
            SurveyResultV2 surveyResult = surveyResultV2Repository
                    .findByIdAndSubmission_MemberId(surveyResultId, memberId)
                    .orElseThrow(SurveyResultNotFoundException::new);
            if (user.getGender() == null) {
                throw new ProfileRequiredException("시즌을 생성하려면 먼저 gender를 설정해야 합니다.");
            }

            AiTraitsRequest traits = coreScoresToTraits(surveyResult.getCoreScoresJson());
            // User.gender는 이 프로젝트 관례상 대문자("MALE"/"FEMALE")로 저장되지만, AI 계약의
            // gender는 소문자(male|female)다 — 여기서만 변환하고 기본 성별을 추측하지는 않는다.
            AiCharacterProfileRequest representative = new AiCharacterProfileRequest(
                    user.getGender().toLowerCase(), traits);
            inputValidator.validateCharacterProfile(representative);
            CharacterProfileDto clientProfile = toClientProfile(representative);
            String profileJson = writeProfileJson(clientProfile);

            // 원격 호출 "이전에" 커밋 — 사용자가 직후 재설문해도 이 snapshot과 idempotency key는 바뀌지 않는다.
            creationInput = stateWriter.persistPendingCreationInput(
                    memberId, surveyResultId, profileJson, sha256Hex(profileJson));
        }

        CharacterProfileDto clientProfile = readProfileJson(creationInput.getProfileJson());
        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        CreateSeasonRequestDto request = CreateSeasonRequestDto.of(clientProfile);
        SeasonResponseDto response = aiServiceClient.createSeason(
                ownerSubject, creationInput.getIdempotencyKey(), request);

        AiSeason season = stateWriter.persistNewSeasonFromSurvey(
                memberId, response, creationInput.getSurveyResult().getId());
        stateWriter.markCreationInputSucceeded(creationInput.getId());
        return toStatusView(season, stateWriter.findCharacters(season.getId()));
    }

    // ---------------------------------------------------------------- 상태 조회/복구

    @Override
    public AiSeasonStatusView getStatus(Long memberId) {
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        SeasonResponseDto latest = aiServiceClient.getSeason(ownerSubjectProvider.resolve(memberId), season.getSeasonId());
        AiSeason synced = stateWriter.syncSeason(season.getId(), latest.revision(), latest.currentEpisode());
        return toStatusView(synced, stateWriter.findCharacters(synced.getId()));
    }

    // ---------------------------------------------------------------- generation

    @Override
    public AiEpisodeProgressView requestGeneration(Long memberId, int episodeNumber) {
        validateEpisodeNumber(episodeNumber);
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        String ownerSubject = ownerSubjectProvider.resolve(memberId);

        AiEpisodeProgress existing = episodeProgressRepository
                .findBySeason_IdAndEpisodeNumber(season.getId(), episodeNumber).orElse(null);
        if (existing != null && existing.isGenerationAccepted()) {
            // 이미 접수됨: 중복 generation 방지 (요구사항 4절).
            return toProgressView(existing);
        }

        // generation 요청 전 최신 상태를 확인하고 revision을 expected_revision에 넣는다 (요구사항 5.2).
        SeasonResponseDto latest = aiServiceClient.getSeason(ownerSubject, season.getSeasonId());
        int revision = latest.revision();

        String businessKey = "season:" + season.getSeasonId() + ":episode:" + episodeNumber + ":generation";
        UUID idempotencyKey = idempotencyKeyService.resolveOrIssue(
                memberId, AiIdempotencyActionType.EPISODE_GENERATION, businessKey);

        JobResponseDto jobResponse;
        try {
            jobResponse = aiServiceClient.requestGeneration(
                    ownerSubject, idempotencyKey, season.getSeasonId(), episodeNumber, new GenerationRequestDto(revision));
        } catch (AiRevisionConflictException conflict) {
            // 진행 순서상 애매함이 없는 동작이므로, 최신 revision으로 한 번만 자동 재판단한다 (요구사항 6절).
            SeasonResponseDto refreshed = aiServiceClient.getSeason(ownerSubject, season.getSeasonId());
            jobResponse = aiServiceClient.requestGeneration(ownerSubject, idempotencyKey, season.getSeasonId(),
                    episodeNumber, new GenerationRequestDto(refreshed.revision()));
        }

        stateWriter.syncSeason(season.getId(), latest.revision(), latest.currentEpisode());
        AiEpisodeProgress progress = stateWriter.persistGenerationAccepted(season.getId(), episodeNumber, jobResponse.jobId());
        return toProgressView(progress);
    }

    // ---------------------------------------------------------------- selection (1화/3화)

    @Override
    public AiSelectionView submitEpisode1Selection(Long memberId, UUID partnerId) {
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        ensureEpisodeGenerationSucceeded(season, EPISODE_1);

        AiSelection existing = selectionRepository
                .findBySeason_IdAndEpisodeNumber(season.getId(), EPISODE_1).orElse(null);
        if (existing != null) {
            // 최초 선택 후 수정 endpoint가 없다 (요구사항 5.4) — 재요청은 멱등하게 기존 값을 반환한다.
            return toSelectionView(existing);
        }

        List<AiSeasonCharacter> characters = seasonCharacterRepository.findAllBySeason_Id(season.getId());
        assertValidCandidate(characters, partnerId);

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        UUID sourceEventId = UUID.randomUUID();
        String businessKey = "season:" + season.getSeasonId() + ":episode:1:selection";
        UUID idempotencyKey = idempotencyKeyService.resolveOrIssue(
                memberId, AiIdempotencyActionType.EPISODE_SELECTION, businessKey);

        SelectionRequestDto request = new SelectionRequestDto(season.getRevision(), sourceEventId,
                SelectionRequestDto.SOURCE_USER, partnerId, null);
        SelectionResponseDto response = submitSelectionWithRetryOnConflict(ownerSubject, idempotencyKey, season, EPISODE_1, request);

        AiSelection saved = stateWriter.persistSelection(
                season.getId(), EPISODE_1, AiSelectionSource.USER, sourceEventId, null, response);
        return toSelectionView(saved);
    }

    @Override
    public AiSelectionView submitEpisode3MinigameResult(Long memberId, boolean success, UUID verifiedPartnerId) {
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        ensureEpisodeGenerationSucceeded(season, EPISODE_3);

        if (success && verifiedPartnerId == null) {
            throw new AiInputValidationException("미니게임 성공 시 verifiedPartnerId가 필요합니다.");
        }
        if (!success && verifiedPartnerId != null) {
            throw new AiInputValidationException("미니게임 실패 결과에는 partnerId를 포함하지 않는다 (요구사항 5.4).");
        }

        AiSelection existing = selectionRepository
                .findBySeason_IdAndEpisodeNumber(season.getId(), EPISODE_3).orElse(null);
        if (existing != null) {
            return toSelectionView(existing);
        }

        if (success) {
            List<AiSeasonCharacter> characters = seasonCharacterRepository.findAllBySeason_Id(season.getId());
            assertValidCandidate(characters, verifiedPartnerId);
        }

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        UUID sourceEventId = UUID.randomUUID();
        String businessKey = "season:" + season.getSeasonId() + ":episode:3:selection";
        UUID idempotencyKey = idempotencyKeyService.resolveOrIssue(
                memberId, AiIdempotencyActionType.EPISODE_SELECTION, businessKey);

        String gameResult = success ? SelectionRequestDto.GAME_RESULT_SUCCESS : SelectionRequestDto.GAME_RESULT_FAILURE;
        SelectionRequestDto request = new SelectionRequestDto(season.getRevision(), sourceEventId,
                SelectionRequestDto.SOURCE_MINIGAME, verifiedPartnerId, gameResult);
        SelectionResponseDto response = submitSelectionWithRetryOnConflict(ownerSubject, idempotencyKey, season, EPISODE_3, request);

        AiGameResult gameResultEnum = success ? AiGameResult.SUCCESS : AiGameResult.FAILURE;
        AiSelection saved = stateWriter.persistSelection(
                season.getId(), EPISODE_3, AiSelectionSource.MINIGAME, sourceEventId, gameResultEnum, response);
        return toSelectionView(saved);
    }

    private SelectionResponseDto submitSelectionWithRetryOnConflict(
            String ownerSubject, UUID idempotencyKey, AiSeason season, int episodeNumber, SelectionRequestDto request) {
        try {
            return aiServiceClient.submitSelection(ownerSubject, idempotencyKey, season.getSeasonId(), episodeNumber, request);
        } catch (AiRevisionConflictException conflict) {
            SeasonResponseDto refreshed = aiServiceClient.getSeason(ownerSubject, season.getSeasonId());
            SelectionRequestDto retried = new SelectionRequestDto(
                    refreshed.revision(), request.sourceEventId(), request.source(),
                    request.partnerId(), request.gameResult());
            return aiServiceClient.submitSelection(ownerSubject, idempotencyKey, season.getSeasonId(), episodeNumber, retried);
        }
    }

    // ---------------------------------------------------------------- reroll (2화/4화)

    @Override
    public AiRerollView requestReroll(Long memberId, int episodeNumber, UUID requestedPartnerId) {
        if (!REROLLABLE_EPISODES.contains(episodeNumber)) {
            throw new AiRerollNotAllowedException("reroll은 2화, 4화에만 제공됩니다.");
        }
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        AiEpisodeProgress progress = getEpisodeProgressOrThrow(season, episodeNumber);
        if (progress.getJobStatus() != AiJobStatus.SUCCEEDED || progress.getVersionId() == null) {
            throw new AiEpisodeProgressionException(episodeNumber + "화가 아직 완료되지 않아 reroll할 수 없습니다.");
        }

        // 다음 화 generation이 이미 접수된 경우 로컬 상태만으로 먼저 빠르게 막는다 (요구사항 5.5).
        episodeProgressRepository.findBySeason_IdAndEpisodeNumber(season.getId(), episodeNumber + 1)
                .filter(AiEpisodeProgress::isGenerationAccepted)
                .ifPresent(nextEpisode -> {
                    throw new AiRerollNotAllowedException(
                            (episodeNumber + 1) + "화 generation이 이미 접수되어 reroll이 잠겼습니다.");
                });

        List<AiSeasonCharacter> characters = seasonCharacterRepository.findAllBySeason_Id(season.getId());
        assertValidCandidate(characters, requestedPartnerId);

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        String businessKey = "season:" + season.getSeasonId() + ":episode:" + episodeNumber
                + ":reroll:" + requestedPartnerId;
        UUID idempotencyKey = idempotencyKeyService.resolveOrIssue(
                memberId, AiIdempotencyActionType.EPISODE_REROLL, businessKey);

        UUID expectedVersionId = progress.getVersionId();
        RerollRequestDto request = new RerollRequestDto(season.getRevision(), expectedVersionId, requestedPartnerId);
        JobResponseDto jobResponse;
        try {
            jobResponse = aiServiceClient.requestReroll(ownerSubject, idempotencyKey, season.getSeasonId(), episodeNumber, request);
        } catch (AiRevisionConflictException conflict) {
            SeasonResponseDto refreshed = aiServiceClient.getSeason(ownerSubject, season.getSeasonId());
            RerollRequestDto retried = new RerollRequestDto(refreshed.revision(), expectedVersionId, requestedPartnerId);
            jobResponse = aiServiceClient.requestReroll(ownerSubject, idempotencyKey, season.getSeasonId(), episodeNumber, retried);
        }

        AiRerollRequest saved = stateWriter.persistRerollAccepted(
                season.getId(), episodeNumber, expectedVersionId, requestedPartnerId, idempotencyKey, jobResponse.jobId());
        return toRerollView(saved);
    }

    @Override
    public AiRerollView getRerollStatus(Long memberId, int episodeNumber) {
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        AiRerollRequest reroll = stateWriter.findLatestReroll(season.getId(), episodeNumber)
                .orElseThrow(() -> new AiRerollNotFoundException(episodeNumber));
        return toRerollView(reroll);
    }

    private AiRerollView toRerollView(AiRerollRequest reroll) {
        return new AiRerollView(reroll.getJobId(), reroll.getJobStatus().name(), reroll.getErrorCode());
    }

    // ---------------------------------------------------------------- 메시지 조회

    @Override
    public AiEpisodeMessagesView getEpisodeMessages(Long memberId, int episodeNumber, Integer afterSequence, Integer limit) {
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        AiEpisodeProgress progress = getEpisodeProgressOrThrow(season, episodeNumber);
        if (progress.getJobStatus() != AiJobStatus.SUCCEEDED || progress.getVersionId() == null) {
            throw new AiEpisodeProgressionException(episodeNumber + "화 generation이 아직 완료되지 않았습니다.");
        }

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        int effectiveLimit = (limit == null) ? 50 : Math.min(Math.max(limit, 1), 100);

        EpisodeResponseDto response;
        try {
            response = aiServiceClient.getEpisode(
                    ownerSubject, season.getSeasonId(), episodeNumber, progress.getVersionId(), afterSequence, effectiveLimit);
        } catch (AiVersionSupersededException superseded) {
            // 서로 다른 version의 페이지를 합치지 않고 새 active version의 첫 페이지부터 다시 읽는다 (요구사항 5.6).
            log.info("episode version superseded, restarting from new active version: episode={}", episodeNumber);
            response = aiServiceClient.getEpisode(
                    ownerSubject, season.getSeasonId(), episodeNumber, null, null, effectiveLimit);
            stateWriter.persistVersionReplaced(progress.getId(), response.versionId());
        }

        UUID representativeId = resolveRepresentativeId(season);
        return toMessagesView(episodeNumber, response, representativeId);
    }

    @Override
    public void markMessagesSeen(Long memberId, int episodeNumber, int sequence) {
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        stateWriter.markMessagesSeen(season.getId(), episodeNumber, sequence);
    }

    // ---------------------------------------------------------------- 리포트

    @Override
    public AiReportView getReport(Long memberId) {
        AiSeason season = getOwnedSeasonOrThrow(memberId);
        ensureEpisodeGenerationSucceeded(season, FINAL_EPISODE);

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        ReportResponseDto response = aiServiceClient.getReport(ownerSubject, season.getSeasonId());

        stateWriter.markSeasonCompleted(season.getId());

        return new AiReportView(response.finalPartnerId(), response.narrative(),
                response.highlights(), response.renderMode());
    }

    // ---------------------------------------------------------------- 내부 헬퍼

    private AiSeason getOwnedSeasonOrThrow(Long memberId) {
        return seasonRepository.findByMemberId(memberId)
                .orElseThrow(AiSeasonNotFoundException::new);
    }

    private AiEpisodeProgress getEpisodeProgressOrThrow(AiSeason season, int episodeNumber) {
        return episodeProgressRepository.findBySeason_IdAndEpisodeNumber(season.getId(), episodeNumber)
                .orElseThrow(() -> new AiEpisodeProgressionException(
                        episodeNumber + "화 generation이 아직 접수되지 않았습니다."));
    }

    private void ensureEpisodeGenerationSucceeded(AiSeason season, int episodeNumber) {
        AiEpisodeProgress progress = getEpisodeProgressOrThrow(season, episodeNumber);
        if (progress.getJobStatus() != AiJobStatus.SUCCEEDED) {
            throw new AiEpisodeProgressionException(episodeNumber + "화가 아직 완료되지 않았습니다.");
        }
    }

    private void assertValidCandidate(List<AiSeasonCharacter> characters, UUID partnerId) {
        if (partnerId == null) {
            throw new AiInputValidationException("partnerId가 없습니다.");
        }
        boolean isValidCandidate = characters.stream()
                .anyMatch(c -> c.getRole() == AiCharacterRole.CANDIDATE && c.getCharacterId().equals(partnerId));
        if (!isValidCandidate) {
            // 요구사항 3.3, 7절: 클라이언트가 보낸 partner ID를 검증 없이 신뢰하지 않는다.
            throw new AiCandidateInvalidException("partnerId가 현재 시즌의 candidate 목록에 없습니다.");
        }
    }

    private void validateEpisodeNumber(int episodeNumber) {
        if (episodeNumber < 1 || episodeNumber > 5) {
            throw new AiInputValidationException("episodeNumber는 1..5 범위여야 합니다.");
        }
    }

    private static final ObjectMapper PROFILE_JSON_MAPPER = new ObjectMapper();

    /** survey_results.core_scores(snake_case, {"score":..,"evidence_weight":..,...} 형태)에서 score만 뽑는다. */
    private AiTraitsRequest coreScoresToTraits(String coreScoresJson) {
        try {
            JsonNode root = PROFILE_JSON_MAPPER.readTree(coreScoresJson);
            return new AiTraitsRequest(
                    scoreOf(root, "affection_expression"), scoreOf(root, "relationship_anxiety"),
                    scoreOf(root, "relationship_avoidance"), scoreOf(root, "emotional_attunement"),
                    scoreOf(root, "relationship_initiative"), scoreOf(root, "practical_priority"),
                    scoreOf(root, "reassurance_need"), scoreOf(root, "jealousy_reactivity"),
                    scoreOf(root, "relationship_energy_dependence"), scoreOf(root, "emotional_suppression"),
                    scoreOf(root, "conflict_confrontation"), scoreOf(root, "relationship_pace"),
                    scoreOf(root, "interest_expression_frequency"));
        } catch (Exception e) {
            throw new IllegalStateException("저장된 core_scores를 파싱할 수 없습니다.", e);
        }
    }

    private BigDecimal scoreOf(JsonNode root, String dimensionKey) {
        return root.get(dimensionKey).get("score").decimalValue();
    }

    private String writeProfileJson(CharacterProfileDto profile) {
        try {
            return PROFILE_JSON_MAPPER.writeValueAsString(profile);
        } catch (Exception e) {
            throw new IllegalStateException("시즌 생성 입력을 JSON으로 직렬화할 수 없습니다.", e);
        }
    }

    private CharacterProfileDto readProfileJson(String json) {
        try {
            return PROFILE_JSON_MAPPER.readValue(json, CharacterProfileDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("저장된 시즌 생성 입력을 파싱할 수 없습니다.", e);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private CharacterProfileDto toClientProfile(AiCharacterProfileRequest r) {
        var t = r.traits();
        TraitsDto traits = new TraitsDto(
                t.affectionExpression(), t.relationshipAnxiety(), t.relationshipAvoidance(),
                t.emotionalAttunement(), t.relationshipInitiative(), t.practicalPriority(),
                t.reassuranceNeed(), t.jealousyReactivity(), t.relationshipEnergyDependence(),
                t.emotionalSuppression(), t.conflictConfrontation(), t.relationshipPace(),
                t.interestExpressionFrequency());
        return new CharacterProfileDto(r.gender(), traitsWireAdapter.forWire(traits));
    }

    private AiSeasonStatusView toStatusView(AiSeason season, List<AiSeasonCharacter> characters) {
        List<AiCharacterView> characterViews = characters.stream()
                .map(c -> new AiCharacterView(
                        c.getCharacterId(), c.getRole().name(), c.getGender()))
                .collect(Collectors.toList());
        return new AiSeasonStatusView(
                season.getSeasonId(), season.getRevision(), season.getCurrentEpisode(),
                season.getStatus().name(), characterViews, season.getSurveyResultId());
    }

    private AiEpisodeProgressView toProgressView(AiEpisodeProgress progress) {
        return new AiEpisodeProgressView(
                progress.getEpisodeNumber(), progress.getJobId(),
                progress.getJobStatus() != null ? progress.getJobStatus().name() : null,
                progress.getVersionId(), progress.getErrorCode());
    }

    private AiSelectionView toSelectionView(AiSelection selection) {
        return new AiSelectionView(selection.getAiSelectionId(), selection.getPartnerId(), selection.getRevisionAfter());
    }

    private AiEpisodeMessagesView toMessagesView(int episodeNumber, EpisodeResponseDto response, UUID representativeId) {
        List<AiMessageView> messages = response.messages().stream()
                .map(m -> toMessageView(m, representativeId))
                .collect(Collectors.toList());
        AiTopicView topic = response.topic() == null ? null
                : new AiTopicView(response.topic().id(), response.topic().title(), response.topic().category());
        return new AiEpisodeMessagesView(
                episodeNumber, response.versionId(), topic, messages, response.hasMore(), response.nextAfterSequence());
    }

    private AiMessageView toMessageView(MessageResponseDto m, UUID representativeId) {
        // scene_kind는 group|candidates_only|one_to_one만 허용한다 (요구사항 5.6).
        // fromWire()가 알 수 없는 값이면 AiContractViolationException을 던진다.
        AiSceneKind sceneKind = AiSceneKind.fromWire(m.sceneKind());
        // speaker_id가 representative ID와 같을 때만 사용자 대변 AI 발화로 표시한다 (요구사항 5.1).
        boolean fromRepresentative = representativeId != null && representativeId.equals(m.speakerId());
        return new AiMessageView(m.messageId(), m.sequence(), sceneKind, m.speakerId(), fromRepresentative, m.text());
    }

    private UUID resolveRepresentativeId(AiSeason season) {
        return seasonCharacterRepository
                .findFirstBySeason_IdAndRole(season.getId(), AiCharacterRole.REPRESENTATIVE)
                .map(AiSeasonCharacter::getCharacterId)
                .orElse(null);
    }
}
