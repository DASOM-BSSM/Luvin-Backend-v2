package com.luvin.ai.service.story;

import com.luvin.ai.client.dto.TraitsDto;
import com.luvin.ai.client.story.SeasonDesignResponse;
import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.domain.AiBreadRelation;
import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiEpisodeContent;
import com.luvin.ai.domain.AiEpisodeUnlock;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSpeechLevel;
import com.luvin.ai.domain.AiSeasonCharacter;
import com.luvin.ai.domain.AiSeasonDesign;
import com.luvin.ai.domain.AiSeasonChoice;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiSeasonStoryReport;
import com.luvin.ai.domain.AiStoryContentStatus;
import com.luvin.ai.domain.AiStoryFeedback;
import com.luvin.ai.domain.AiStoryMessageLike;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.story.AiStageIntroView;
import com.luvin.ai.domain.PartnerLetter;
import com.luvin.ai.repository.AiBreadRelationRepository;
import com.luvin.ai.repository.AiEpisodeContentRepository;
import com.luvin.ai.repository.AiEpisodeUnlockRepository;
import com.luvin.ai.repository.AiSeasonCharacterRepository;
import com.luvin.ai.repository.AiSeasonCreationInputRepository;
import com.luvin.ai.repository.AiSeasonDesignRepository;
import com.luvin.ai.repository.AiSeasonChoiceRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.repository.AiSeasonStoryReportRepository;
import com.luvin.ai.repository.AiStoryFeedbackRepository;
import com.luvin.ai.repository.AiStoryMessageLikeRepository;
import com.luvin.ai.repository.PartnerLetterRepository;
import com.luvin.ai.service.exception.AiInsufficientTokensException;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import com.luvin.token.domain.TokenHistory;
import com.luvin.token.domain.TokenHistoryType;
import com.luvin.token.domain.UserToken;
import com.luvin.token.repository.TokenHistoryRepository;
import com.luvin.token.repository.UserTokenRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * 장면 블록 회차의 모든 짧은 write transaction. {@link com.luvin.ai.service.AiSeasonStateWriter}와 같은 이유로
 * FastAPI 호출을 하는 {@link AiStoryService}와 분리했다(self-invocation 방지, 원격 호출 중 DB 커넥션 미점유).
 */
@Service
@RequiredArgsConstructor
public class AiStoryStateWriter {

    /** recent_memories는 최근 3회차 주요 장면만 남긴다. */
    static final int RECENT_MEMORY_LIMIT = 3;

    private static final List<AiCastRole> CANDIDATE_ROLES = List.of(AiCastRole.AVOIDANT, AiCastRole.ANXIOUS, AiCastRole.SECURE);
    private static final List<AiCastRole> SUPPORTING_ROLES = List.of(AiCastRole.FRIEND, AiCastRole.MOOD_MAKER);

    private final AiSeasonRepository seasonRepository;
    private final AiSeasonCharacterRepository characterRepository;
    private final AiBreadRelationRepository relationRepository;
    private final AiSeasonChoiceRepository choiceRepository;
    private final AiEpisodeContentRepository contentRepository;
    private final AiSeasonStoryReportRepository reportRepository;
    private final PartnerLetterRepository letterRepository;
    private final AiStoryFeedbackRepository feedbackRepository;
    private final AiStoryMessageLikeRepository likeRepository;
    private final AiEpisodeUnlockRepository unlockRepository;
    private final AiSeasonDesignRepository designRepository;
    private final AiSeasonCreationInputRepository creationInputRepository;
    private final UserTokenRepository userTokenRepository;
    @PersistenceContext
    private EntityManager entityManager;
    private final TokenHistoryRepository tokenHistoryRepository;

    /** 생성(GET content)·완료(POST complete) 접수 결과. started면 호출부가 백그라운드 작업을 시작한다. */
    public record GenerationTicket(Long contentPk, boolean started, int round) {
    }

    /**
     * 새 흐름을 시작한다: 배경 저장, 출연진 배정(representative → 내 빵, AI candidate 3명 → 회피형/불안형/안정형,
     * 친구·분위기 메이커 추가), 출연 빵별 관계 기록 생성. 이미 시작한 시즌이면 아무것도 하지 않는다.
     *
     * 이름: 내 빵은 사용자 닉네임과 설문 빵 타입, 나머지는 "중립 형용사 + 빵 종류"로 시즌 동안 고정한다. 한 시즌 안에서
     * 이름과 빵 종류가 겹치지 않는다(빵 종류 8종, 형용사 12개에서 섞어 뽑는다).
     * 성별(계약 7장): 친구만 사용자와 같고, 후보 셋과 분위기 메이커는 사용자와 다르다. 사용자 성별 형식 그대로 저장한다.
     * 후보 셋을 만나는 순서(1~3화 1:1 상대)는 랜덤으로 정해 candidateOrder에 둔다.
     */
    @Transactional
    public void startStory(Long seasonPk, AiSeasonSetting setting, String myName, String myBreadType, String myGender,
                           List<String> breadTypes, RandomGenerator random) {
        AiSeason season = seasonRepository.findWithLockById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        // 요청 앞부분(open-in-view)에서 이미 읽은 시즌이면 잠금 조회도 캐시된 값을 돌려준다 — 잠금을 기다리는 사이 다른
        // 요청이 시작을 마쳤을 수 있으니 잠근 뒤 DB 값으로 다시 읽는다(동시에 새 시즌을 만들 때 출연진이 두 번 생기지 않게).
        entityManager.refresh(season);
        if (season.isStoryStarted()) {
            return;
        }
        List<AiSeasonCharacter> characters = characterRepository.findAllBySeason_Id(seasonPk);
        AiSeasonCharacter representative = characters.stream().filter(AiSeasonCharacter::isRepresentative)
                .findFirst().orElseThrow(() -> new IllegalStateException("시즌에 representative가 없습니다."));
        List<AiSeasonCharacter> candidates = characters.stream()
                .filter(c -> c.getRole() == AiCharacterRole.CANDIDATE)
                .sorted(Comparator.comparing(AiSeasonCharacter::getId))
                .toList();
        if (candidates.size() < CANDIDATE_ROLES.size()) {
            throw new IllegalStateException("시즌 candidate가 3명 미만입니다: " + candidates.size());
        }

        List<String> otherBreads = new ArrayList<>(breadTypes);
        otherBreads.remove(myBreadType);
        Collections.shuffle(otherBreads, random);
        List<String> adjectives = new ArrayList<>(AiStoryCatalog.NEUTRAL_ADJECTIVES);
        Collections.shuffle(adjectives, random);
        List<Integer> meetingOrder = new ArrayList<>(List.of(1, 2, 3));
        Collections.shuffle(meetingOrder, random);
        String otherGender = AiStoryCatalog.oppositeGender(myGender);

        representative.assignCast(AiCastRole.MY_BREAD, myName, myBreadType, myGender, null);
        List<AiSeasonCharacter> cast = new ArrayList<>();
        int nameIndex = 0;
        for (int i = 0; i < CANDIDATE_ROLES.size(); i++, nameIndex++) {
            candidates.get(i).assignCast(CANDIDATE_ROLES.get(i), adjectives.get(nameIndex) + " " + otherBreads.get(nameIndex),
                    otherBreads.get(nameIndex), otherGender, meetingOrder.get(i));
            cast.add(candidates.get(i));
        }
        for (AiCastRole role : SUPPORTING_ROLES) {
            String gender = role == AiCastRole.FRIEND ? myGender : otherGender;
            cast.add(characterRepository.save(new AiSeasonCharacter(season, UUID.randomUUID(), role,
                    adjectives.get(nameIndex) + " " + otherBreads.get(nameIndex), otherBreads.get(nameIndex), gender)));
            nameIndex++;
        }
        for (AiSeasonCharacter member : cast) {
            relationRepository.save(new AiBreadRelation(season.getMemberId(), season, member.getCharacterId(),
                    AiChoiceRules.INITIAL_AFFINITY.get(member.getCastRole()), AiChoiceRules.INITIAL_STATUS_TEXT,
                    AiSpeechLevel.initial(setting)));
        }
        season.startStory(setting);
    }

    // ---------------------------------------------------------------- 새 시즌 설정(2시즌부터)

    @Transactional(readOnly = true)
    public Optional<AiSeasonDesignRecord> findSeasonDesign(Long seasonPk) {
        return designRepository.findBySeason_Id(seasonPk)
                .map(d -> AiStoryJson.read(d.getDesignJson(), AiSeasonDesignRecord.class));
    }

    /** 이 시즌 생성 입력(설문 결과로 만든 내 빵 성향)의 13개 점수. 입력이 없으면 empty. */
    @Transactional(readOnly = true)
    public Optional<TraitsDto> seasonTraits(Long memberId, int seasonNumber) {
        return creationInputRepository.findByMemberIdAndSeasonNumber(memberId, seasonNumber)
                .map(input -> AiStoryJson.read(input.getProfileJson(), CreationProfile.class))
                .map(CreationProfile::traits);
    }

    /** 시즌 생성 입력(CharacterProfileDto JSON) 중 traits만 읽는다(나머지 필드는 무시). */
    private record CreationProfile(TraitsDto traits) {
    }

    /** seasonNumber보다 앞선 시즌 전부를 최근 순으로 읽는다(새 시즌 설정 입력). */
    @Transactional(readOnly = true)
    public List<AiPastSeason> pastSeasons(Long memberId, int seasonNumber) {
        return seasonRepository.findAllByMemberIdOrderBySeasonNumberDesc(memberId).stream()
                .filter(s -> s.getSeasonNumber() < seasonNumber)
                .map(this::pastSeason)
                .toList();
    }

    private AiPastSeason pastSeason(AiSeason season) {
        Long seasonPk = season.getId();
        Map<UUID, AiSeasonCharacter> cast = characterRepository.findAllBySeason_Id(seasonPk).stream()
                .filter(c -> c.getCastRole() != null)
                .collect(Collectors.toMap(AiSeasonCharacter::getCharacterId, Function.identity()));
        AiStageIntroView stageIntro = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, AiStoryCatalog.FIRST_ORDER)
                .map(c -> AiStoryJson.read(c.getStageIntroJson(), AiStageIntroView.class))
                .orElse(null);
        AiPastSeason.Partner partner = null;
        AiSeasonCharacter partnerCharacter = season.getRomancePartnerId() == null ? null
                : cast.get(season.getRomancePartnerId());
        if (partnerCharacter != null) {
            String oneLine = stageIntro == null ? null : stageIntro.cast().stream()
                    .filter(c -> c.breadId().equals(partnerCharacter.getCharacterId()))
                    .map(AiStageIntroView.StageCast::description)
                    .findFirst().orElse(null);
            partner = new AiPastSeason.Partner(partnerCharacter.getName(), partnerCharacter.getCastRole(), oneLine);
        }
        List<AiPastSeason.Choice> choices = choiceRepository.findAllBySeason_IdOrderByPointIdAsc(seasonPk).stream()
                .map(c -> {
                    AiSeasonCharacter chosen = c.getBreadId() == null ? null : cast.get(c.getBreadId());
                    return new AiPastSeason.Choice(c.getPointId(), c.getEpisodeOrder(),
                            chosen == null ? null : chosen.getName(), chosen == null ? null : chosen.getCastRole(),
                            c.isAccepted(), c.getReasonCode());
                })
                .toList();
        AiReportView report = reportRepository.findBySeason_Id(seasonPk)
                .map(r -> AiStoryJson.read(r.getReportJson(), AiReportView.class))
                .orElse(null);
        List<String> feedbacks = feedbackRepository.findTop10BySeason_IdOrderByUpdatedAtDesc(seasonPk).stream()
                .map(AiStoryFeedback::getText).toList();
        List<String> likedLines = likeRepository.findTop10BySeason_IdOrderByCreatedAtDesc(seasonPk).stream()
                .map(AiStoryMessageLike::getMessageText).toList();
        List<String> castNames = cast.values().stream()
                .filter(c -> c.getCastRole() != AiCastRole.MY_BREAD)
                .sorted(Comparator.comparing(AiSeasonCharacter::getCastRole))
                .map(AiSeasonCharacter::getName)
                .toList();
        return new AiPastSeason(season.getSeasonNumber(), season.getSetting(), season.getRoute(), partner, choices,
                report, feedbacks, likedLines, castNames, stageIntro == null ? null : stageIntro.subtitle());
    }

    /**
     * 새 시즌 설정을 한 번만 반영한다: 시즌을 잠그고, 아직 설정이 없을 때만 NPC 이름·빵 종류를 설정대로 바꾸고 설정을 저장한다
     * (출연진 UUID·성별·만나는 순서는 시즌 시작 때 정한 그대로). 이미 저장한 설정이 있으면 바꾸지 않고 그 값을 돌려준다 —
     * 늦게 끝난 다른 생성이 결과를 다시 덮어쓰지 않는다.
     */
    @Transactional
    public AiSeasonDesignRecord applySeasonDesign(Long seasonPk, AiSeasonDesignRecord record) {
        AiSeason season = seasonRepository.findWithLockById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        Optional<AiSeasonDesign> existing = designRepository.findBySeason_Id(seasonPk);
        if (existing.isPresent()) {
            return AiStoryJson.read(existing.get().getDesignJson(), AiSeasonDesignRecord.class);
        }
        Map<AiCastRole, SeasonDesignResponse.Cast> assigned = AiSeasonDesigns.assign(record.design());
        for (AiSeasonCharacter character : characterRepository.findAllBySeason_Id(seasonPk)) {
            SeasonDesignResponse.Cast designed = character.getCastRole() == null ? null
                    : assigned.get(character.getCastRole());
            if (designed != null) {
                character.renameForSeasonDesign(designed.name(), designed.breadType());
            }
        }
        designRepository.save(new AiSeasonDesign(season, AiStoryJson.write(record)));
        return record;
    }

    /**
     * 시즌·회차·관계를 한 시점의 값으로 읽는다. 백그라운드 회차 완료가 커밋되는 사이에 읽어도 completedOrders와
     * currentOrder가 어긋나지 않게 하기 위해서다.
     * - 영속성 컨텍스트 비우기: open-in-view라 요청 앞부분(GET /me의 시즌 조회 등)에서 읽은 엔티티가 요청의 컨텍스트에
     *   남아 있다. 그 컨텍스트에 트랜잭션이 없으면 REQUIRES_NEW도 같은 컨텍스트를 쓰므로, 그대로 읽으면 시즌은 그때의
     *   값(완료 전)이 오고 회차는 새로 조회한 값(완료 후)이 온다. 바깥 트랜잭션 안이면 REQUIRES_NEW가 새 컨텍스트를
     *   쓰므로 비워도 영향이 없다. 호출부는 이 뒤에 엔티티의 기본 컬럼만 읽는다(지연 로딩 없음).
     * - REPEATABLE_READ: READ COMMITTED면 쿼리마다 DB 스냅샷이 달라 같은 어긋남이 생길 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AiStorySnapshot snapshot(Long seasonPk) {
        entityManager.clear();
        AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        List<AiStoryCastMember> cast = characterRepository.findAllBySeason_Id(seasonPk).stream()
                .filter(c -> c.getCastRole() != null)
                .sorted(Comparator.comparing(AiSeasonCharacter::getCastRole))
                .map(c -> new AiStoryCastMember(c.getCharacterId(), c.getCastRole(), c.getName(), c.getBreadType(),
                        c.getGender(), c.getCandidateOrder()))
                .toList();
        List<AiStorySnapshot.Relation> relations = relationRepository.findAllBySeason_Id(seasonPk).stream()
                .map(r -> new AiStorySnapshot.Relation(r.getTargetBreadId(), r.getAffinity(), r.getRejectCount(),
                        r.getStatusText(), r.getMemorySummary(), AiStoryJson.readStrings(r.getRecentMemoriesJson()),
                        AiStoryJson.readStrings(r.getKnownFactsJson()), AiStoryJson.readThreads(r.getOpenThreadsJson()),
                        r.getChoiceMemory(),
                        r.getSpeechLevel() != null ? r.getSpeechLevel() : AiSpeechLevel.initial(season.getSetting())))
                .toList();
        List<AiStorySnapshot.Choice> choices = choiceRepository.findAllBySeason_IdOrderByPointIdAsc(seasonPk).stream()
                .map(c -> new AiStorySnapshot.Choice(c.getPointId(), c.getEpisodeOrder(), c.getBreadId(),
                        c.isAccepted(), c.getReasonCode(), c.getResultBlocksJson(), c.effectiveResultStatus(),
                        c.currentResultRound(), c.getResultRequestedAt(), c.getResultErrorCode()))
                .toList();
        List<AiStorySnapshot.Content> contents = contentRepository.findAllBySeason_IdOrderByEpisodeOrderAsc(seasonPk)
                .stream()
                .map(c -> new AiStorySnapshot.Content(c.getId(), c.getEpisodeOrder(), c.getStatus(), c.getRoute(),
                        c.getErrorCode(), c.getIntroJson(), c.getStageIntroJson(), c.getBlocksJson(),
                        c.getSummaryJson(), c.getRequestedAt(), c.isCompleted(), c.getCompletionRequestedAt(),
                        c.getCompletionErrorCode(), c.getGenerationStage(),
                        Boolean.TRUE.equals(c.getGenerationRegenerating()), c.getCompletionStage(),
                        Boolean.TRUE.equals(c.getCompletionRegenerating())))
                .toList();
        return new AiStorySnapshot(season.getId(), season.getSeasonId(), season.getMemberId(), season.getSeasonNumber(),
                season.getStatus(), season.getSetting(), season.getCurrentOrder(), season.getRoute(),
                season.getRomancePartnerId(), cast, relations, choices, contents, findUnlockedOrders(seasonPk));
    }

    /**
     * 회차 생성을 접수한다. 처음이거나, 이미 앱에 알린 FAILED였거나, GENERATING인 채 staleBefore보다 오래 멈춰 있으면
     * started=true로 돌려주고 호출부가 생성을 시작한다. 아직 알리지 않은 FAILED는 다시 생성하지 않고 그대로 두어 이번
     * 응답이 failed가 되게 한다 — 그다음 요청(앱의 "다시 시도")부터 다시 생성한다. 동시에 처음 요청한 두 요청 중 진 쪽은 unique 제약 위반을 이 짧은 트랜잭션
     * 안에서만 받고(REQUIRES_NEW + saveAndFlush) 이긴 쪽 row를 그대로 쓴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GenerationTicket beginGeneration(Long seasonPk, int order, AiStoryRoute route, LocalDateTime staleBefore) {
        AiEpisodeContent existing = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, order).orElse(null);
        if (existing == null) {
            AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
            try {
                AiEpisodeContent created = contentRepository.saveAndFlush(
                        new AiEpisodeContent(season.getMemberId(), season, order, route));
                return new GenerationTicket(created.getId(), true, created.currentGenerationRound());
            } catch (DataIntegrityViolationException raceLoser) {
                AiEpisodeContent winner = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, order)
                        .orElseThrow(() -> raceLoser);
                return new GenerationTicket(winner.getId(), false, winner.currentGenerationRound());
            }
        }
        boolean stale = existing.getStatus() == AiStoryContentStatus.GENERATING
                && existing.getRequestedAt().isBefore(staleBefore);
        if (existing.getStatus() == AiStoryContentStatus.FAILED && existing.reportFailureOnce()) {
            contentRepository.saveAndFlush(existing);
            return new GenerationTicket(existing.getId(), false, existing.currentGenerationRound());
        }
        if (existing.getStatus() == AiStoryContentStatus.FAILED || stale) {
            existing.restartGeneration(route);
            contentRepository.saveAndFlush(existing);
            return new GenerationTicket(existing.getId(), true, existing.currentGenerationRound());
        }
        return new GenerationTicket(existing.getId(), false, existing.currentGenerationRound());
    }

    /** 생성 결과 저장. 아직 생성 중이고 같은 생성 회차일 때만 저장한다(늦게 끝난 이전 회차 결과는 버린다). */
    @Transactional
    public void saveGenerated(Long contentPk, int round, String introJson, String stageIntroJson, String blocksJson) {
        contentRepository.findById(contentPk)
                .filter(c -> isCurrentGeneration(c, round))
                .ifPresent(c -> c.markReady(introJson, stageIntroJson, blocksJson));
    }

    @Transactional
    public void markGenerationFailed(Long contentPk, int round, String errorCode) {
        contentRepository.findById(contentPk)
                .filter(c -> isCurrentGeneration(c, round))
                .ifPresent(c -> c.markFailed(errorCode));
    }

    /** 이어서 진행할 회차 생성·완료 한 건. round는 그때의 생성(또는 완료) 회차 번호다. */
    public record PendingWork(Long contentPk, Long memberId, Long seasonPk, int order, int round) {
    }

    @Transactional(readOnly = true)
    public List<PendingWork> findInterruptedGenerations(LocalDateTime staleBefore) {
        return contentRepository.findGeneratingSince(staleBefore).stream()
                .map(c -> new PendingWork(c.getId(), c.getMemberId(), c.getSeason().getId(), c.getEpisodeOrder(),
                        c.currentGenerationRound()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PendingWork> findInterruptedCompletions(LocalDateTime staleBefore) {
        return contentRepository.findCompletingSince(staleBefore).stream()
                .map(c -> new PendingWork(c.getId(), c.getMemberId(), c.getSeason().getId(), c.getEpisodeOrder(),
                        c.currentCompletionRound()))
                .toList();
    }

    @Transactional
    public void recordGenerationProgress(Long contentPk, int round, String stage, UUID jobId, boolean regenerating) {
        contentRepository.updateGenerationProgress(contentPk, round, stage, jobId, regenerating);
    }

    private static boolean isCurrentGeneration(AiEpisodeContent content, int round) {
        return content.getStatus() == AiStoryContentStatus.GENERATING && content.currentGenerationRound() == round;
    }

    /**
     * 회차 완료를 접수한다. 이미 완료했거나, 완료 중인데 staleBefore보다 최근에 시작했으면 started=false(진행 중인 쪽을
     * 그대로 둔다). 처음이거나, 실패했거나, 완료 중인 채 오래 멈춰 있으면(서버 재시작 등) started=true로 돌려주고 호출부가
     * 백그라운드 완료를 시작한다. 동시에 두 요청이 오면 진 쪽은 낙관적 락 충돌을 이 짧은 트랜잭션 안에서만 받는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GenerationTicket beginCompletion(Long seasonPk, int order, LocalDateTime staleBefore) {
        AiEpisodeContent content = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, order).orElseThrow();
        if (content.isCompleted()
                || (content.isCompleting() && !content.getCompletionRequestedAt().isBefore(staleBefore))) {
            return new GenerationTicket(content.getId(), false, content.currentCompletionRound());
        }
        content.beginCompletion();
        contentRepository.saveAndFlush(content);
        return new GenerationTicket(content.getId(), true, content.currentCompletionRound());
    }

    @Transactional
    public void markCompletionFailed(Long contentPk, int round, String errorCode) {
        contentRepository.findById(contentPk)
                .filter(c -> c.currentCompletionRound() == round)
                .ifPresent(c -> c.markCompletionFailed(errorCode));
    }

    @Transactional
    public void recordCompletionProgress(Long contentPk, int round, String stage, UUID jobId, boolean regenerating) {
        contentRepository.updateCompletionProgress(contentPk, round, stage, jobId, regenerating);
    }

    /**
     * 선택을 기록하고 관계·흐름에 반영한다: 수락이면 호감 +8, 거절이면 거절 횟수 +1, ③이면 route 결정.
     * 같은 지점을 동시에 두 번 보내면 unique 제약 위반(DataIntegrityViolationException)으로 트랜잭션 전체가 롤백된다.
     */
    @Transactional
    public void saveChoice(Long seasonPk, int pointId, int order, UUID breadId, AiChoiceJudge.Decision decision,
                           /** 혼자 있기처럼 바로 만든 결과면 그 JSON, 백그라운드에서 만들 거면 null(GENERATING으로 저장). */
                           String resultBlocksJson) {
        AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        choiceRepository.saveAndFlush(new AiSeasonChoice(season.getMemberId(), season, pointId, order, breadId,
                decision.accepted(), decision.reasonCode(), resultBlocksJson));

        if (breadId != null) {
            AiBreadRelation relation = relationRepository.findBySeason_IdAndTargetBreadId(seasonPk, breadId)
                    .orElseThrow(() -> new IllegalStateException("관계 기록이 없는 빵: " + breadId));
            if (decision.accepted()) {
                relation.addAffinity(AiChoiceRules.ACCEPTED_AFFINITY_BONUS);
            } else {
                relation.recordRejection();
            }
            if (pointId == AiChoiceRules.CONFESSION_POINT && decision.accepted()) {
                relation.updateStatusText(AiChoiceRules.ROMANCE_STATUS_TEXT);
            }
        }
        if (pointId == AiChoiceRules.CONFESSION_POINT) {
            boolean romance = breadId != null && decision.accepted();
            season.applyRoute(romance ? AiStoryRoute.ROMANCE : AiStoryRoute.SOLO, romance ? breadId : null);
        }
    }

    /** 선택 결과 장면 생성 한 건(선택 지점, 그 회차, 생성 회차 번호). */
    public record ChoiceResultWork(Long memberId, Long seasonPk, int pointId, int order, int round) {
    }

    /**
     * 실패했거나 멈춘(GENERATING인 채 staleBefore보다 오래된) 선택 결과 장면 생성을 새 회차로 다시 시작한다. 다시 시작했으면
     * 그 회차 번호, 아니면(이미 끝났거나 진행 중) empty. 선택 row를 잠가 동시에 두 요청이 다시 시작하지 않게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Integer> restartChoiceResult(Long seasonPk, int pointId, LocalDateTime staleBefore) {
        AiSeasonChoice choice = choiceRepository.findWithLockBySeasonPkAndPointId(seasonPk, pointId).orElseThrow();
        entityManager.refresh(choice);
        AiStoryContentStatus status = choice.effectiveResultStatus();
        boolean stale = status == AiStoryContentStatus.GENERATING
                && (choice.getResultRequestedAt() == null || choice.getResultRequestedAt().isBefore(staleBefore));
        if (status != AiStoryContentStatus.FAILED && !stale) {
            return Optional.empty();
        }
        choice.restartResult();
        return Optional.of(choice.currentResultRound());
    }

    /** 결과 장면 저장. 아직 생성 중이고 같은 생성 회차일 때만 저장한다(늦게 끝난 이전 회차 결과는 버린다). */
    @Transactional
    public void saveChoiceResult(Long seasonPk, int pointId, int round, String resultBlocksJson) {
        choiceRepository.findBySeason_IdAndPointId(seasonPk, pointId)
                .filter(c -> isCurrentChoiceResult(c, round))
                .ifPresent(c -> c.markResultReady(resultBlocksJson));
    }

    @Transactional
    public void markChoiceResultFailed(Long seasonPk, int pointId, int round, String errorCode) {
        choiceRepository.findBySeason_IdAndPointId(seasonPk, pointId)
                .filter(c -> isCurrentChoiceResult(c, round))
                .ifPresent(c -> c.markResultFailed(errorCode));
    }

    @Transactional(readOnly = true)
    public List<ChoiceResultWork> findInterruptedChoiceResults(LocalDateTime staleBefore) {
        return choiceRepository.findResultGeneratingSince(staleBefore).stream()
                .map(c -> new ChoiceResultWork(c.getMemberId(), c.getSeason().getId(), c.getPointId(),
                        c.getEpisodeOrder(), c.currentResultRound()))
                .toList();
    }

    private static boolean isCurrentChoiceResult(AiSeasonChoice choice, int round) {
        return choice.effectiveResultStatus() == AiStoryContentStatus.GENERATING
                && choice.currentResultRound() == round;
    }

    /** 정리 단계(5-5)가 제안한 빵 하나의 관계 기록 갱신안. breadId는 서비스가 계약 id를 UUID로 바꾼 값이다. */
    public record RelationChange(UUID breadId, String memorySummary, String recentMemory, List<String> newFacts,
                                 List<StoryTypes.Thread> threadsAdd, List<String> threadsResolved, String statusText,
                                 Integer affinityDelta, String choiceMemory,
                                 /** formal | casual | null. 배경 규칙에 맞을 때만 반영한다({@link AiSpeechLevel#allowsChange}). */
                                 String speechLevel) {
    }

    /** 회차 쪽지(5-6). partnerId는 발신 빵(계약 id를 UUID로 바꾼 값, 모르면 null). */
    public record EpisodeNote(UUID partnerId, String body, String renderMode) {
    }

    /**
     * 회차 완료: 정리 단계 결과를 관계 기록에 반영(호감 변화는 -5~+5로 잘라서), 회차 요약 저장, 회차 쪽지 저장,
     * 다음 회차로 진행. 10화면 최종 리포트를 저장하고 시즌을 완료 처리한다. 이미 완료한 회차면 아무것도 하지 않는다.
     */
    @Transactional
    public void completeEpisode(Long seasonPk, int order, int round, String episodeSummary,
                                List<RelationChange> changes, EpisodeNote note, String reportJson) {
        AiSeason season = seasonRepository.findById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        AiEpisodeContent content = contentRepository.findBySeason_IdAndEpisodeOrder(seasonPk, order).orElseThrow();
        if (content.isCompleted() || content.currentCompletionRound() != round) {
            return; // 이미 반영했거나(중복 완료) 그사이 새 완료 회차가 시작됐다(늦은 결과는 버린다).
        }

        Map<UUID, AiBreadRelation> relations = relationRepository.findAllBySeason_Id(seasonPk).stream()
                .collect(Collectors.toMap(AiBreadRelation::getTargetBreadId, Function.identity()));
        // 해결된 떡밥은 어느 빵의 갱신안에 들어 있든 시즌의 모든 관계 기록에서 지운다 — 같은 떡밥이 여러 빵에 저장돼 있어도
        // 해결 상태가 어긋나지 않게(한 빵에서는 풀렸는데 다른 빵에서 계속 다음 회차 입력으로 나가지 않게) 한다.
        Set<String> resolvedThreads = changes.stream()
                .filter(c -> c.threadsResolved() != null)
                .flatMap(c -> c.threadsResolved().stream())
                .collect(Collectors.toSet());
        if (!resolvedThreads.isEmpty()) {
            relations.values().forEach(r -> r.applyMemory(null, null, null,
                    updateThreads(r, null, List.copyOf(resolvedThreads)), null, null));
        }
        for (RelationChange change : changes) {
            AiBreadRelation relation = relations.get(change.breadId());
            if (relation == null) {
                continue;
            }
            if (change.affinityDelta() != null) {
                relation.addAffinity(AiChoiceRules.clampSummaryDelta(change.affinityDelta()));
            }
            AiSpeechLevel current = relation.getSpeechLevel() != null
                    ? relation.getSpeechLevel() : AiSpeechLevel.initial(season.getSetting());
            AiSpeechLevel requested = AiSpeechLevel.fromWire(change.speechLevel());
            boolean romancePartner = season.getRoute() == AiStoryRoute.ROMANCE
                    && relation.getTargetBreadId().equals(season.getRomancePartnerId());
            relation.changeSpeechLevel(AiSpeechLevel.allowsChange(season.getSetting(), current, requested, romancePartner)
                    ? requested : current);
            String statusText = AiChoiceRules.ROMANCE_STATUS_TEXT.equals(relation.getStatusText())
                    ? null : change.statusText();
            relation.applyMemory(change.memorySummary(), appendRecentMemory(relation, change.recentMemory()),
                    appendFacts(relation, change.newFacts()),
                    updateThreads(relation, change.threadsAdd(), change.threadsResolved()), statusText,
                    change.choiceMemory());
        }

        content.markCompleted(AiStoryJson.write(Map.of("episodeSummary", episodeSummary)));
        if (!letterRepository.existsBySeason_IdAndEpisodeOrder(seasonPk, order)) {
            letterRepository.save(PartnerLetter.forEpisode(season.getMemberId(), season, order,
                    note.partnerId(), note.body(), note.renderMode()));
        }

        if (order < AiStoryCatalog.FINAL_ORDER) {
            season.advanceOrder(order + 1);
            return;
        }
        if (reportRepository.findBySeason_Id(seasonPk).isEmpty()) {
            reportRepository.save(new AiSeasonStoryReport(season, reportJson));
        }
        season.complete();
    }

    // ---------------------------------------------------------------- 1:1 장면 피드백·좋아요

    /** 메시지당 피드백 하나. 이미 있으면 글만 바꾼다. */
    @Transactional
    public AiStoryFeedback saveFeedback(Long seasonPk, int order, String blockId, UUID messageId, String messageText,
                                       String text) {
        Optional<AiStoryFeedback> existing = feedbackRepository.findBySeason_IdAndMessageId(seasonPk, messageId);
        if (existing.isPresent()) {
            existing.get().rewrite(text);
            return existing.get();
        }
        return feedbackRepository.save(new AiStoryFeedback(seasonRepository.getReferenceById(seasonPk), order,
                blockId, messageId, messageText, text));
    }

    /** liked면 좋아요를 켜고(이미 켜져 있으면 그대로), 아니면 끈다. */
    @Transactional
    public void setLike(Long seasonPk, int order, String blockId, UUID messageId, String messageText, boolean fromMe,
                        boolean liked) {
        Optional<AiStoryMessageLike> existing = likeRepository.findBySeason_IdAndMessageId(seasonPk, messageId);
        if (liked && existing.isEmpty()) {
            likeRepository.save(new AiStoryMessageLike(seasonRepository.getReferenceById(seasonPk), order, blockId,
                    messageId, messageText, fromMe));
        } else if (!liked) {
            existing.ifPresent(likeRepository::delete);
        }
    }

    @Transactional(readOnly = true)
    public List<AiStoryFeedback> findFeedbacks(Long seasonPk, int order) {
        return feedbackRepository.findAllBySeason_IdAndEpisodeOrder(seasonPk, order);
    }

    /** 이번 시즌에 이미 보낸 회차 쪽지 본문(회차 순). */
    @Transactional(readOnly = true)
    public List<String> findEpisodeNoteBodies(Long seasonPk) {
        return letterRepository.findAllBySeason_IdAndEpisodeOrderIsNotNullOrderByEpisodeOrderAsc(seasonPk).stream()
                .map(PartnerLetter::getMessage)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> findLikedMessageIds(Long seasonPk, int order) {
        return likeRepository.findAllBySeason_IdAndEpisodeOrder(seasonPk, order).stream()
                .map(AiStoryMessageLike::getMessageId).toList();
    }

    /** 학습 입력(learnedProfile.feedbacks): 최근 10개. */
    @Transactional(readOnly = true)
    public List<StoryTypes.Feedback> recentFeedbacks(Long seasonPk) {
        return feedbackRepository.findTop10BySeason_IdOrderByUpdatedAtDesc(seasonPk).stream()
                .map(f -> new StoryTypes.Feedback(f.getEpisodeOrder(), f.getMessageText(), f.getText()))
                .toList();
    }

    /** 학습 입력(learnedProfile.likedMessages): 내 빵의 대사 중 최근 10개. */
    @Transactional(readOnly = true)
    public List<String> recentLikedMyMessages(Long seasonPk) {
        return likeRepository.findTop10BySeason_IdAndFromMeTrueOrderByCreatedAtDesc(seasonPk).stream()
                .map(AiStoryMessageLike::getMessageText).toList();
    }

    // ---------------------------------------------------------------- 회차 열기(토큰)

    /**
     * 4~10화를 연다. 처음 열 때만 토큰을 차감하고(charge=false면 차감 없이 기록만), 열린 기록과 차감을 한 트랜잭션에서 한다.
     * 이미 연 회차면 아무것도 하지 않는다. 같은 회차를 동시에 두 번 열면 unique 제약 위반
     * (DataIntegrityViolationException)으로 이 트랜잭션 전체가 롤백된다 — 호출부가 다시 읽어 열린 상태로 응답한다.
     *
     * @throws com.luvin.ai.service.exception.AiInsufficientTokensException 토큰이 부족할 때(409 INSUFFICIENT_TOKENS)
     */
    @Transactional
    public void unlockEpisode(Long seasonPk, Long memberId, int order, int cost, boolean charge) {
        if (unlockRepository.existsBySeason_IdAndEpisodeOrder(seasonPk, order)) {
            return;
        }
        int spent = charge ? cost : 0;
        unlockRepository.saveAndFlush(new AiEpisodeUnlock(seasonRepository.getReferenceById(seasonPk), order, spent));
        if (spent == 0) {
            return;
        }
        UserToken userToken = userTokenRepository.findByMemberId(memberId)
                .orElseGet(() -> userTokenRepository.save(new UserToken(memberId, 0, 0)));
        if (userToken.getTokenBalance() < spent) {
            throw new AiInsufficientTokensException(spent, userToken.getTokenBalance());
        }
        userToken.use(spent);
        tokenHistoryRepository.save(new TokenHistory(memberId, TokenHistoryType.USE, -spent, "러빈지옥 " + order + "화 열기"));
    }

    @Transactional(readOnly = true)
    public List<Integer> findUnlockedOrders(Long seasonPk) {
        return unlockRepository.findAllBySeason_Id(seasonPk).stream().map(AiEpisodeUnlock::getEpisodeOrder).toList();
    }

    @Transactional(readOnly = true)
    public int tokenBalance(Long memberId) {
        return userTokenRepository.findByMemberId(memberId).map(UserToken::getTokenBalance).orElse(0);
    }

    @Transactional(readOnly = true)
    public Optional<String> findReportJson(Long seasonPk) {
        return reportRepository.findBySeason_Id(seasonPk).map(AiSeasonStoryReport::getReportJson);
    }

    /** 개발 전용: 시즌을 새 흐름 시작 전 상태로 되돌린다. 기존 5화 흐름 데이터(AI 시즌, 쪽지)는 건드리지 않는다. */
    @Transactional
    public void resetStory(Long seasonPk) {
        AiSeason season = seasonRepository.findWithLockById(seasonPk).orElseThrow(AiSeasonNotFoundException::new);
        choiceRepository.deleteAllBySeason_Id(seasonPk);
        relationRepository.deleteAllBySeason_Id(seasonPk);
        contentRepository.deleteAllBySeason_Id(seasonPk);
        reportRepository.deleteAllBySeason_Id(seasonPk);
        feedbackRepository.deleteAllBySeason_Id(seasonPk);
        likeRepository.deleteAllBySeason_Id(seasonPk);
        unlockRepository.deleteAllBySeason_Id(seasonPk);
        designRepository.deleteAllBySeason_Id(seasonPk);
        letterRepository.deleteAllBySeason_IdAndEpisodeOrderIsNotNull(seasonPk);
        characterRepository.deleteAllBySeason_IdAndRole(seasonPk, AiCharacterRole.SUPPORTING);
        characterRepository.findAllBySeason_Id(seasonPk).forEach(AiSeasonCharacter::clearCast);
        season.resetStory();
    }

    private static String appendFacts(AiBreadRelation relation, List<String> newFacts) {
        if (newFacts == null || newFacts.isEmpty()) {
            return null;
        }
        java.util.LinkedHashSet<String> facts = new java.util.LinkedHashSet<>(
                AiStoryJson.readStrings(relation.getKnownFactsJson()));
        facts.addAll(newFacts);
        return AiStoryJson.write(List.copyOf(facts));
    }

    /** 풀린 떡밥(id)은 빼고 새 떡밥은 더한다. 같은 id가 다시 오면 문구를 바꾼다. */
    private static String updateThreads(AiBreadRelation relation, List<StoryTypes.Thread> added, List<String> resolved) {
        if ((added == null || added.isEmpty()) && (resolved == null || resolved.isEmpty())) {
            return null;
        }
        Map<String, StoryTypes.Thread> threads = new java.util.LinkedHashMap<>();
        AiStoryJson.readThreads(relation.getOpenThreadsJson()).forEach(t -> threads.put(t.id(), t));
        if (resolved != null) {
            resolved.forEach(threads::remove);
        }
        if (added != null) {
            added.forEach(t -> threads.put(t.id(), t));
        }
        return AiStoryJson.write(List.copyOf(threads.values()));
    }

    /**
     * 최근 기억에 하나를 더하고 오래된 것부터 3개만 남긴다. 같은 문장(공백 차이 무시)이 이미 있으면 겹쳐 쌓지 않고 그 기억을
     * 가장 최근으로 옮긴다 — 같은 소재가 최근 기억을 채워 다음 회차 입력을 한 소재로 몰지 않게 한다.
     */
    static String appendRecentMemory(AiBreadRelation relation, String recentMemory) {
        if (recentMemory == null || recentMemory.isBlank()) {
            return null;
        }
        String key = normalizeMemory(recentMemory);
        List<String> memories = new ArrayList<>(AiStoryJson.readStrings(relation.getRecentMemoriesJson()));
        memories.removeIf(m -> normalizeMemory(m).equals(key));
        memories.add(recentMemory.strip());
        while (memories.size() > RECENT_MEMORY_LIMIT) {
            memories.remove(0);
        }
        return AiStoryJson.write(memories);
    }

    private static String normalizeMemory(String memory) {
        return memory == null ? "" : memory.replaceAll("\\s+", " ").strip();
    }
}
