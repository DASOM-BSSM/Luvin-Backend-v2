package com.luvin.ai.service.story;

import com.fasterxml.jackson.core.type.TypeReference;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.client.story.AiStoryClient;
import com.luvin.ai.client.story.ChoiceResultRequest;
import com.luvin.ai.client.story.ChoiceResultResponse;
import com.luvin.ai.client.story.EpisodeNoteRequest;
import com.luvin.ai.client.story.EpisodeNoteResponse;
import com.luvin.ai.client.story.EpisodeRequest;
import com.luvin.ai.client.story.EpisodeResponse;
import com.luvin.ai.client.story.InterviewRequest;
import com.luvin.ai.client.story.InterviewResponse;
import com.luvin.ai.client.story.SeasonReportRequest;
import com.luvin.ai.client.story.SeasonReportResponse;
import com.luvin.ai.client.story.StageIntroRequest;
import com.luvin.ai.client.story.StageIntroResponse;
import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.client.story.SummarizeRequest;
import com.luvin.ai.client.story.SummarizeResponse;
import com.luvin.ai.config.AiStoryProperties;
import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonStatus;
import com.luvin.ai.domain.AiStoryContentStatus;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.domain.AiStoryFeedback;
import com.luvin.ai.dto.AiHighlightView;
import com.luvin.ai.dto.AiMessageLikeView;
import com.luvin.ai.dto.AiReportHighlightsView;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.story.AiChoiceOutcomeView;
import com.luvin.ai.dto.story.AiEpisodeContentView;
import com.luvin.ai.dto.story.AiEpisodeUnlockView;
import com.luvin.ai.dto.story.AiSceneBlockView;
import com.luvin.ai.dto.story.AiStageIntroView;
import com.luvin.ai.dto.story.AiStoryFeedbackView;
import com.luvin.ai.dto.story.AiStoryIntroView;
import com.luvin.ai.dto.story.AiStoryMessageView;
import com.luvin.ai.dto.story.AiStoryStateView;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.security.OwnerSubjectProvider;
import com.luvin.ai.service.exception.AiCandidateInvalidException;
import com.luvin.ai.service.exception.AiInputValidationException;
import com.luvin.ai.service.exception.AiSeasonCompletedException;
import com.luvin.ai.service.exception.AiSeasonNotFoundException;
import com.luvin.ai.service.exception.AiStoryConflictException;
import com.luvin.ai.service.exception.AiStoryGenerationFailedException;
import com.luvin.ai.service.story.AiEpisodePlan.BlockPlan;
import com.luvin.common.exception.UserNotFoundException;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 장면 블록 회차(1~10화) 흐름. 설계·판정은 코드({@link AiEpisodePlanner}, {@link AiChoiceJudge})로 하고,
 * 대사·정리·쪽지·리포트는 FastAPI({@link AiStoryClient}, 공통 데이터 계약 5장)에 맡긴다. Spring은 Gemini를 직접
 * 부르지 않는다. FastAPI와는 역할 고정 빵 id(me, avoidant, …)로, 앱과는 시즌 캐릭터 UUID로 주고받는다
 * ({@link AiStoryContractMapper}).
 *
 * 회차 콘텐츠는 GET content가 생성을 접수하고 백그라운드에서 만든다 — 그동안 응답 status는 generating이다.
 * 선택 결과 장면과 완료 정리는 요청 안에서 바로 FastAPI를 호출한다. FastAPI 응답은 형식 검증 후 실패하면 1회
 * 재시도하고, 그래도 실패하면 아무것도 저장하지 않고 오류를 돌려준다.
 *
 * 다음 회차는 이전 회차를 완료한 뒤에만 생성하고, 선택이 있는 회차(3·5·6·8화)는 선택을 기록해야 완료할 수 있다 —
 * 그래서 4화는 ①, 6화는 ②, 7화는 ③, 9화는 ④가 기록된 뒤에만 생성된다.
 */
@Service
public class AiStoryService {

    private static final Logger log = LoggerFactory.getLogger(AiStoryService.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final String DEFAULT_MEMORY = "아직 함께한 기억이 없어요";
    public static final String FORMAT_BLOCKS = "blocks";
    /** 1화 무대 소개에서 내 빵의 역할 표기. */
    static final String MY_BREAD_STAGE_ROLE = "나";
    private static final TypeReference<List<AiSceneBlockView>> BLOCK_LIST = new TypeReference<>() {
    };

    private final AiStoryStateWriter stateWriter;
    private final AiStoryClient storyClient;
    private final AiSeasonRepository seasonRepository;
    private final UserRepository userRepository;
    private final OwnerSubjectProvider ownerSubjectProvider;
    private final AiLearningDataProvider learningDataProvider;
    private final AiBreadCatalog breadCatalog;
    private final AiEpisodeReleasePolicy releasePolicy;
    private final AiStoryProperties properties;
    private final ExecutorService executor;

    public AiStoryService(AiStoryStateWriter stateWriter, AiStoryClient storyClient,
                          AiSeasonRepository seasonRepository, UserRepository userRepository,
                          OwnerSubjectProvider ownerSubjectProvider,
                          AiLearningDataProvider learningDataProvider, AiBreadCatalog breadCatalog,
                          AiEpisodeReleasePolicy releasePolicy, AiStoryProperties properties,
                          @Qualifier("aiStoryExecutor") ExecutorService executor) {
        this.stateWriter = stateWriter;
        this.storyClient = storyClient;
        this.seasonRepository = seasonRepository;
        this.userRepository = userRepository;
        this.ownerSubjectProvider = ownerSubjectProvider;
        this.learningDataProvider = learningDataProvider;
        this.breadCatalog = breadCatalog;
        this.releasePolicy = releasePolicy;
        this.properties = properties;
        this.executor = executor;
    }

    // ---------------------------------------------------------------- 회차 콘텐츠

    /**
     * 저장된 회차가 있으면 그대로, 없으면 생성을 접수하고 generating으로 응답한다. 새 흐름을 아직 시작하지 않은 시즌은
     * 1화 요청에서 시작한다(나이로 배경 결정, 출연진 배정·이름 짓기) — 나이가 없으면 409 AGE_REQUIRED.
     */
    public AiEpisodeContentView getContent(Long memberId, int order) {
        validateOrder(order);
        AiSeason season = currentSeason(memberId);
        AiStorySnapshot snapshot = stateWriter.snapshot(season.getId());

        if (!snapshot.isStarted()) {
            if (season.getStatus() == AiSeasonStatus.COMPLETED) {
                throw new AiSeasonCompletedException();
            }
            if (order != AiStoryCatalog.FIRST_ORDER) {
                throw new AiStoryConflictException(AiStoryConflictException.STORY_NOT_STARTED, "1화부터 시작해주세요.");
            }
            // 시즌 생성 때 시작하지 못한 시즌(이 정책 전에 만든 시즌)만 여기서 시작한다.
            startStoryForNewSeason(memberId, season.getId());
            snapshot = stateWriter.snapshot(season.getId());
        }

        Optional<AiStorySnapshot.Content> existing = snapshot.content(order);
        if (existing.isPresent() && existing.get().status() == AiStoryContentStatus.READY) {
            return toContentView(snapshot, existing.get());
        }
        if (snapshot.status() == AiSeasonStatus.COMPLETED) {
            throw new AiSeasonCompletedException();
        }
        if (order > AiStoryCatalog.FIRST_ORDER
                && snapshot.content(order - 1).map(c -> !c.completed()).orElse(true)) {
            throw new AiStoryConflictException(AiStoryConflictException.PREVIOUS_EPISODE_NOT_COMPLETED,
                    (order - 1) + "화를 먼저 완료해주세요.");
        }
        if (!releasePolicy.isReleased(season, order)) {
            throw new AiStoryConflictException(AiStoryConflictException.EPISODE_NOT_RELEASED,
                    order + "화는 아직 공개되지 않았습니다.");
        }
        if (!AiStoryCatalog.isFree(order) && !snapshot.unlockedOrders().contains(order)) {
            throw new AiStoryConflictException(AiStoryConflictException.EPISODE_NOT_UNLOCKED,
                    order + "화를 먼저 열어주세요.");
        }

        AiStoryStateWriter.GenerationTicket ticket;
        try {
            ticket = stateWriter.beginGeneration(season.getId(), order, order >= 7 ? snapshot.route() : null,
                    LocalDateTime.now().minus(properties.generationStaleAfter()));
        } catch (ObjectOptimisticLockingFailureException concurrentRestart) {
            ticket = null; // 다른 요청이 먼저 다시 시작했다 — 그쪽 생성 상태를 그대로 보여준다.
        }
        if (ticket != null && ticket.started()) {
            Long contentPk = ticket.contentPk();
            Long seasonPk = season.getId();
            executor.execute(() -> generate(memberId, seasonPk, order, contentPk));
        }
        AiStorySnapshot latest = stateWriter.snapshot(season.getId());
        return toContentView(latest, latest.content(order).orElseThrow());
    }

    /**
     * 새 시즌의 회차 흐름을 시작한다: 나이로 배경 결정(시즌 동안 고정), 출연진 배정·이름·성별·후보 순서. 시즌 생성 직후
     * 부른다. 이미 시작한 시즌이면 아무것도 하지 않는다. 나이가 없으면 409 AGE_REQUIRED.
     */
    public void startStoryForNewSeason(Long memberId, Long seasonPk) {
        User user = userWithAge(memberId);
        stateWriter.startStory(seasonPk,
                AiSeasonSettingPolicy.decideSetting(user.getAge(), ThreadLocalRandom.current()),
                user.getNickname() != null ? user.getNickname() : user.getName(),
                breadCatalog.breadLabel(user.getPersonalityType()), user.getGender(),
                breadCatalog.allBreadLabels(), ThreadLocalRandom.current());
    }

    // ---------------------------------------------------------------- 회차 열기

    /**
     * 회차 열기. 1~3화는 무료라 차감 없이 열린 응답이고, 4~10화는 처음 열 때만 토큰을 쓴다(이미 연 회차는 같은 응답).
     * 차감은 생성 전에 하고, 열린 뒤 생성이 실패해도 열린 상태로 남는다. 이전 회차를 완료해야 열 수 있다.
     * 토큰이 모자라면 409 INSUFFICIENT_TOKENS.
     */
    public AiEpisodeUnlockView unlock(Long memberId, int order) {
        validateOrder(order);
        AiSeason season = currentSeason(memberId);
        if (AiStoryCatalog.isFree(order)) {
            return new AiEpisodeUnlockView(order, true, stateWriter.tokenBalance(memberId));
        }
        AiStorySnapshot snapshot = startedSnapshot(season);
        boolean opened = snapshot.unlockedOrders().contains(order)
                || snapshot.content(order).map(c -> c.status() == AiStoryContentStatus.READY).orElse(false);
        if (!opened) {
            if (snapshot.status() == AiSeasonStatus.COMPLETED) {
                throw new AiSeasonCompletedException();
            }
            if (snapshot.content(order - 1).map(c -> !c.completed()).orElse(true)) {
                throw new AiStoryConflictException(AiStoryConflictException.PREVIOUS_EPISODE_NOT_COMPLETED,
                        (order - 1) + "화를 먼저 완료해주세요.");
            }
            try {
                stateWriter.unlockEpisode(season.getId(), memberId, order, properties.episodeTokenCost(),
                        properties.tokenChargeEnabled());
            } catch (DataIntegrityViolationException concurrentUnlock) {
                // 같은 회차를 동시에 연 다른 요청이 이겼다 — 그쪽에서 이미 열렸다.
            }
        }
        return new AiEpisodeUnlockView(order, true, stateWriter.tokenBalance(memberId));
    }

    /** 백그라운드 생성. 어떤 오류든 FAILED로 남겨 다음 GET content가 다시 시도하게 한다(스레드 밖으로 던지지 않는다). */
    void generate(Long memberId, Long seasonPk, int order, Long contentPk) {
        try {
            AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
            String ownerSubject = ownerSubjectProvider.resolve(memberId);
            int age = userWithAge(memberId).getAge();
            AiStageIntroView stageIntro = order == AiStoryCatalog.FIRST_ORDER
                    ? requestStageIntro(ownerSubject, snapshot, age)
                    : storedStageIntro(snapshot);
            AiStoryContractMapper mapper = new AiStoryContractMapper(snapshot, stageIntro);
            StoryTypes.GenerationContext context = context(snapshot, mapper, age, memberId);

            AiEpisodePlan plan = AiEpisodePlanner.plan(planInput(snapshot, order));
            List<BlockPlan> designed = plan.episodeBlocks();
            EpisodeRequest request = new EpisodeRequest(context, order, plan.commonEvent(), plan.settingScene(),
                    plan.route() == null ? null : plan.route().wireValue(),
                    mapper.contractIdOrNull(snapshot.romancePartnerId()), plan.referencePlot(),
                    designed.stream().map(mapper::design).toList(), plan.openThreads(), mapper.relationRecords(),
                    mapper.choiceRecords(), usedMaterials(snapshot, order));
            EpisodeResponse episode = callWithRetry("episode",
                    () -> storyClient.writeEpisode(ownerSubject, request),
                    r -> AiStoryResponseValidator.validateEpisode(r, designed.stream().map(mapper::expected).toList()));

            Map<String, StoryTypes.GeneratedBlock> generated = new HashMap<>();
            episode.blocks().forEach(b -> generated.put(b.id(), b));
            Map<String, List<StoryTypes.Message>> interviews = plan.interviewBlocks().isEmpty()
                    ? Map.of() : requestInterviews(ownerSubject, snapshot, mapper, context);

            List<AiSceneBlockView> blocks = toBlockViews(plan, generated, interviews, snapshot, mapper);
            AiStoryIntroView intro = new AiStoryIntroView(episode.intro().title(), episode.intro().episodeName(),
                    episode.intro().situation());
            stateWriter.saveGenerated(contentPk, AiStoryJson.write(intro),
                    order == AiStoryCatalog.FIRST_ORDER ? AiStoryJson.write(stageIntro) : null,
                    AiStoryJson.write(blocks));
        } catch (Exception e) {
            log.warn("story episode generation failed: seasonPk={}, order={}, cause={}",
                    seasonPk, order, e.getClass().getSimpleName());
            stateWriter.markGenerationFailed(contentPk, AiStoryGenerationFailedException.CODE);
        }
    }

    private AiStageIntroView requestStageIntro(String ownerSubject, AiStorySnapshot snapshot, int age) {
        List<StageIntroRequest.Cast> cast = snapshot.cast().stream()
                .map(m -> new StageIntroRequest.Cast(m.castRole().contractId(), m.name(), m.breadType(), m.gender(),
                        m.castRole().fastApiCastRole(), m.castRole().attachmentStyle().wireValue(),
                        m.castRole().seasonRole()))
                .toList();
        StageIntroRequest request = new StageIntroRequest(snapshot.setting().wireValue(),
                AiSeasonSettingPolicy.audience(age), AiSeasonSettingPolicy.ageGroup(age), cast);
        List<String> castIds = cast.stream().map(StageIntroRequest.Cast::breadId).toList();
        StageIntroResponse response = callWithRetry("stage-intro",
                () -> storyClient.stageIntro(ownerSubject, request),
                r -> AiStoryResponseValidator.validateStageIntro(r, castIds));
        Map<String, StageIntroResponse.Cast> byId = new HashMap<>();
        response.cast().forEach(c -> byId.put(c.breadId(), c));
        List<AiStageIntroView.StageCast> stageCast = snapshot.cast().stream()
                .map(m -> {
                    // 내 빵은 역할 "나", 소개 없음(계약 0-1장). 무대 소개는 내 빵 포함 6명 전부다.
                    StageIntroResponse.Cast intro = byId.get(m.castRole().contractId());
                    return m.isMe()
                            ? new AiStageIntroView.StageCast(m.breadId(), m.name(), m.breadType(), MY_BREAD_STAGE_ROLE, null)
                            : new AiStageIntroView.StageCast(m.breadId(), m.name(), m.breadType(), intro.role(),
                                    intro.description());
                })
                .toList();
        return new AiStageIntroView(snapshot.setting().wireValue(), snapshot.setting().title(),
                snapshot.setting().subtitle(), stageCast);
    }

    /** 10화 인터뷰룸(5-4). 빵별 관계 기록과 그 빵에게 한 선택(거절 사유 포함)을 보낸다. */
    private Map<String, List<StoryTypes.Message>> requestInterviews(String ownerSubject, AiStorySnapshot snapshot,
                                                                    AiStoryContractMapper mapper,
                                                                    StoryTypes.GenerationContext context) {
        List<InterviewRequest.Item> items = snapshot.relations().stream()
                .map(r -> new InterviewRequest.Item(mapper.contractId(r.breadId()), mapper.relationRecord(r.breadId()),
                        snapshot.choices().stream()
                                .filter(c -> r.breadId().equals(c.breadId()))
                                .map(c -> new InterviewRequest.ChoiceHistory(c.pointId(), c.accepted(),
                                        AiChoiceRules.rejectReason(c.accepted(), c.reasonCode())))
                                .toList()))
                .toList();
        InterviewRequest request = new InterviewRequest(context,
                snapshot.route() == null ? null : snapshot.route().wireValue(),
                mapper.contractIdOrNull(snapshot.romancePartnerId()), items);
        List<String> interviewees = items.stream().map(InterviewRequest.Item::breadId).toList();
        InterviewResponse response = callWithRetry("interview",
                () -> storyClient.writeInterview(ownerSubject, request),
                r -> AiStoryResponseValidator.validateInterview(r, interviewees));
        Map<String, List<StoryTypes.Message>> byBread = new HashMap<>();
        response.interviews().forEach(i -> byBread.put(i.breadId(), i.messages()));
        return byBread;
    }

    // ---------------------------------------------------------------- 선택

    /**
     * 3·5·6·8화 끝 선택. 판정은 코드 규칙으로 하고, 결과 장면만 FastAPI가 쓴다(5-3). 혼자 있기는 FastAPI를 부르지 않는다.
     * 같은 선택을 다시 보내면 저장된 결과를 그대로 돌려주고, 다른 빵으로 다시 고르려 하면 409 CHOICE_ALREADY_MADE.
     */
    public AiChoiceOutcomeView choose(Long memberId, int order, int pointId, UUID breadId) {
        validateOrder(order);
        Integer expectedPoint = AiStoryCatalog.pointOf(order);
        if (expectedPoint == null || expectedPoint != pointId) {
            throw new AiInputValidationException(order + "화에는 선택 지점 " + pointId + "이(가) 없습니다.");
        }
        AiSeason season = currentSeason(memberId);
        AiStorySnapshot snapshot = startedSnapshot(season);

        Optional<AiStorySnapshot.Choice> existing = snapshot.choice(pointId);
        if (existing.isPresent()) {
            return existingOutcome(existing.get(), breadId, snapshot);
        }
        if (snapshot.status() == AiSeasonStatus.COMPLETED) {
            throw new AiSeasonCompletedException();
        }
        AiStorySnapshot.Content content = readyContent(snapshot, order);
        if (breadId != null && !choiceCandidates(content).contains(breadId)) {
            throw new AiCandidateInvalidException("breadId가 이 선택 카드의 후보가 아닙니다.");
        }

        AiChoiceJudge.Decision decision = AiChoiceJudge.judge(judgeInput(snapshot, pointId, breadId));
        AiStoryContractMapper mapper = new AiStoryContractMapper(snapshot, storedStageIntro(snapshot));
        BlockPlan reaction = AiEpisodePlanner.planChoiceReaction(pointId, breadId, decision, snapshot.cast());
        AiSceneBlockView.SceneChoiceResult result = new AiSceneBlockView.SceneChoiceResult(pointId, breadId,
                decision.accepted());
        List<AiSceneBlockView> blocks = new ArrayList<>();
        int[] sequence = {1};

        if (breadId == null) {
            blocks.add(new AiSceneBlockView(reaction.id(), AiSceneBlockType.CHOICE_RESULT.wireValue(),
                    mapper.participants(reaction.participantIds()), List.of(), null, null, null, result, null));
        } else {
            String ownerSubject = ownerSubjectProvider.resolve(memberId);
            int age = userWithAge(memberId).getAge();
            String friendId = AiCastRole.FRIEND.contractId();
            ChoiceResultRequest request = new ChoiceResultRequest(context(snapshot, mapper, age, memberId), order,
                    pointId, mapper.contractId(breadId), decision.accepted(),
                    AiChoiceRules.rejectReason(decision.accepted(), decision.reasonCode()), decision.shortReply(),
                    mapper.relationRecord(breadId));
            Set<String> speakers = mapper.contractIds(reaction.participantIds());
            ChoiceResultResponse response = callWithRetry("choice-result",
                    () -> storyClient.writeChoiceResult(ownerSubject, request),
                    r -> AiStoryResponseValidator.validateChoiceResult(r, speakers, decision.accepted(), friendId));

            blocks.add(new AiSceneBlockView(reaction.id(), AiSceneBlockType.CHOICE_RESULT.wireValue(),
                    mapper.participants(reaction.participantIds()), mapper.messageViews(response.messages(), sequence),
                    null, null, null, result, null));
            UUID me = snapshot.me().breadId();
            List<ChoiceResultResponse.FallbackBlock> fallbacks =
                    response.fallbackBlocks() == null ? List.of() : response.fallbackBlocks();
            for (ChoiceResultResponse.FallbackBlock fallback : fallbacks) {
                List<UUID> participants = StoryTypes.ALONE.equals(fallback.pairTarget())
                        ? List.of(me) : List.of(me, mapper.uuid(friendId));
                blocks.add(new AiSceneBlockView("c" + pointId + "-" + fallback.id(), AiSceneBlockType.PAIR.wireValue(),
                        mapper.participants(participants), mapper.messageViews(fallback.messages(), sequence),
                        null, null, null, null, null));
            }
        }

        try {
            stateWriter.saveChoice(season.getId(), pointId, order, breadId, decision, AiStoryJson.write(blocks));
        } catch (DataIntegrityViolationException raceLoser) {
            AiStorySnapshot reloaded = stateWriter.snapshot(season.getId());
            return existingOutcome(reloaded.choice(pointId).orElseThrow(() -> raceLoser), breadId, reloaded);
        }
        return new AiChoiceOutcomeView(blocks, toStateView(stateWriter.snapshot(season.getId())));
    }

    private AiChoiceOutcomeView existingOutcome(AiStorySnapshot.Choice choice, UUID requestedBreadId,
                                                AiStorySnapshot snapshot) {
        if (!Objects.equals(choice.breadId(), requestedBreadId)) {
            throw new AiStoryConflictException(AiStoryConflictException.CHOICE_ALREADY_MADE, "이미 선택을 마쳤습니다.");
        }
        return new AiChoiceOutcomeView(AiStoryJson.read(choice.resultBlocksJson(), BLOCK_LIST), toStateView(snapshot));
    }

    // ---------------------------------------------------------------- 회차 완료

    /**
     * 회차 완료: 정리 단계(5-5) → 회차 쪽지(5-6) → (10화면) 최종 리포트(5-7)를 FastAPI로 만들고 한 트랜잭션에 저장한다.
     * 하나라도 실패하면 아무것도 저장하지 않는다. 이미 완료한 회차면 상태만 돌려준다.
     */
    public AiStoryStateView complete(Long memberId, int order) {
        validateOrder(order);
        AiSeason season = currentSeason(memberId);
        AiStorySnapshot snapshot = startedSnapshot(season);
        AiStorySnapshot.Content content = readyContent(snapshot, order);
        if (content.completed()) {
            return toStateView(snapshot);
        }
        Integer pointId = AiStoryCatalog.pointOf(order);
        if (pointId != null && snapshot.choice(pointId).isEmpty()) {
            throw new AiStoryConflictException(AiStoryConflictException.CHOICE_REQUIRED, "선택을 먼저 해주세요.");
        }

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        AiStoryContractMapper mapper = new AiStoryContractMapper(snapshot, storedStageIntro(snapshot));
        StoryTypes.GenerationContext context = context(snapshot, mapper, userWithAge(memberId).getAge(), memberId);
        List<String> relationIds = mapper.relationIds();
        List<String> castIds = mapper.castIds();

        List<AiSceneBlockView> resultBlocks = pointId == null ? List.of()
                : AiStoryJson.read(snapshot.choice(pointId).orElseThrow().resultBlocksJson(), BLOCK_LIST);
        SummarizeRequest summarizeRequest = new SummarizeRequest(context, order,
                mapper.sceneBlocks(AiStoryJson.read(content.blocksJson(), BLOCK_LIST), resultBlocks),
                mapper.relationRecords());
        SummarizeResponse summary = callWithRetry("summarize",
                () -> storyClient.summarize(ownerSubject, summarizeRequest),
                r -> AiStoryResponseValidator.validateSummary(r, relationIds));

        String settingScene = AiStoryCatalog.settingScene(snapshot.setting(), order);
        StoryTypes.ChoiceRecord choice = pointId == null ? null : mapper.choiceRecords().stream()
                .filter(c -> c.pointId() == pointId).findFirst().orElse(null);
        List<EpisodeNoteRequest.Relation> noteRelations = summary.relations().stream()
                .map(u -> new EpisodeNoteRequest.Relation(u.breadId(), u.statusText(), u.recentMemory()))
                .toList();
        AiStoryCastMember sender = noteSender(snapshot, content, order);
        String senderStatus = summary.relations().stream()
                .filter(u -> u.breadId().equals(sender.castRole().contractId()))
                .map(SummarizeResponse.RelationUpdate::statusText)
                .findFirst()
                .orElseGet(() -> snapshot.relation(sender.breadId()).map(AiStorySnapshot.Relation::statusText)
                        .orElse(null));
        EpisodeNoteRequest noteRequest = new EpisodeNoteRequest(context, order, settingScene, summary.episodeSummary(),
                choice, noteRelations,
                new EpisodeNoteRequest.Sender(sender.castRole().contractId(), sender.name(),
                        sender.castRole().attachmentStyle().wireValue(), senderStatus),
                stateWriter.findEpisodeNoteBodies(snapshot.seasonPk()).stream().map(AiStoryService::firstSentence)
                        .toList());
        EpisodeNoteResponse note = callWithRetry("episode-note",
                () -> storyClient.writeEpisodeNote(ownerSubject, noteRequest),
                r -> AiStoryResponseValidator.validateNote(r, castIds));

        String reportJson = null;
        if (order == AiStoryCatalog.FINAL_ORDER) {
            List<StoryTypes.EpisodeSummary> summaries = episodeSummaries(snapshot, order, summary.episodeSummary());
            SeasonReportRequest reportRequest = new SeasonReportRequest(context,
                    snapshot.route() == null ? null : snapshot.route().wireValue(),
                    mapper.contractIdOrNull(snapshot.romancePartnerId()), summaries, mapper.choiceRecordsWithReason(),
                    mapper.relationRecords());
            SeasonReportResponse report = callWithRetry("season-report",
                    () -> storyClient.writeSeasonReport(ownerSubject, reportRequest),
                    r -> AiStoryResponseValidator.validateReport(r, castIds));
            reportJson = AiStoryJson.write(toReportView(report, snapshot));
        }

        List<AiStoryStateWriter.RelationChange> changes = summary.relations().stream()
                .map(u -> new AiStoryStateWriter.RelationChange(mapper.uuid(u.breadId()), u.memorySummary(),
                        u.recentMemory(), u.newFacts(), u.threadsAdd(), u.threadsResolved(), u.statusText(),
                        u.affinityDelta(), u.choiceMemory()))
                .toList();
        stateWriter.completeEpisode(season.getId(), order, summary.episodeSummary(), changes,
                new AiStoryStateWriter.EpisodeNote(sender.breadId(), note.body(),
                        note.renderMode()),
                reportJson);
        return toStateView(stateWriter.snapshot(season.getId()));
    }

    // ---------------------------------------------------------------- 1:1 장면 피드백·좋아요

    /**
     * 1:1 장면(pair 블록, 상대가 있는 장면)에서 내 빵의 대사에 남기는 피드백. 메시지당 하나이고 다시 보내면 글을 바꾼다.
     * 회차 본문의 pair 블록과 선택 거절 뒤 친구와의 대체 pair 블록에 남길 수 있다. 혼자인 독백 장면은 안 된다.
     */
    public AiStoryFeedbackView submitFeedback(Long memberId, int order, Integer bodyOrder, String blockId,
                                              UUID messageId, String text) {
        if (text.length() > AiStoryFeedback.TEXT_MAX) {
            throw new AiInputValidationException(AiInputValidationException.FEEDBACK_TOO_LONG,
                    "피드백은 " + AiStoryFeedback.TEXT_MAX + "자 이내여야 합니다.");
        }
        SceneMessage target = findMyPairMessage(memberId, order, bodyOrder, blockId, messageId);
        AiStoryFeedback saved = stateWriter.saveFeedback(target.seasonPk(), order, blockId, messageId,
                target.message().text(), text);
        return new AiStoryFeedbackView(order, saved.getBlockId(), saved.getMessageId(), saved.getText());
    }

    /** 1:1 장면(pair 블록)에서 내 빵의 대사 좋아요. liked로 켜고 끈다. */
    public AiMessageLikeView setLike(Long memberId, int order, Integer bodyOrder, String blockId, UUID messageId,
                                     boolean liked) {
        SceneMessage target = findMyPairMessage(memberId, order, bodyOrder, blockId, messageId);
        stateWriter.setLike(target.seasonPk(), order, blockId, messageId, target.message().text(),
                target.message().fromRepresentative(), liked);
        return new AiMessageLikeView(messageId, liked);
    }

    private record SceneMessage(Long seasonPk, AiStoryMessageView message) {
    }

    /**
     * 피드백·좋아요 대상: 상대가 있는 pair 블록 안의 내 빵 대사(kind chat). 그 외(다른 블록, 혼자인 pair, 독백,
     * 내레이션, 상대의 대사)는 400.
     */
    private SceneMessage findMyPairMessage(Long memberId, int order, Integer bodyOrder, String blockId,
                                           UUID messageId) {
        validateOrder(order);
        if (bodyOrder != null && bodyOrder != order) {
            throw new AiInputValidationException(AiInputValidationException.SCENE_ORDER_MISMATCH,
                    "경로의 order와 body의 order가 다릅니다.");
        }
        AiSeason season = currentSeason(memberId);
        AiStorySnapshot snapshot = startedSnapshot(season);
        AiStorySnapshot.Content content = readyContent(snapshot, order);
        List<AiSceneBlockView> blocks = new ArrayList<>(AiStoryJson.read(content.blocksJson(), BLOCK_LIST));
        Integer pointId = AiStoryCatalog.pointOf(order);
        if (pointId != null) {
            snapshot.choice(pointId).ifPresent(c -> blocks.addAll(AiStoryJson.read(c.resultBlocksJson(), BLOCK_LIST)));
        }
        AiSceneBlockView block = blocks.stream().filter(b -> b.id().equals(blockId)).findFirst()
                .orElseThrow(() -> new AiInputValidationException(AiInputValidationException.SCENE_BLOCK_NOT_FOUND,
                        "이 회차에 없는 blockId입니다."));
        if (!AiSceneBlockType.PAIR.wireValue().equals(block.type()) || block.participants().size() < 2) {
            throw new AiInputValidationException(AiInputValidationException.SCENE_NOT_PAIR,
                    "1:1 장면(상대가 있는 pair 블록)에서만 남길 수 있습니다.");
        }
        AiStoryMessageView message = block.messages().stream().filter(m -> m.messageId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new AiInputValidationException(AiInputValidationException.SCENE_MESSAGE_NOT_FOUND,
                        "messageId가 이 장면의 대사에 없습니다."));
        if (!message.fromRepresentative()) {
            throw new AiInputValidationException(AiInputValidationException.SCENE_NOT_MY_LINE,
                    "내 빵의 대사에만 남길 수 있습니다.");
        }
        if (StoryTypes.KIND_MONOLOGUE.equals(message.kind())) {
            throw new AiInputValidationException(AiInputValidationException.SCENE_MONOLOGUE, "독백에는 남길 수 없습니다.");
        }
        return new SceneMessage(season.getId(), message);
    }

    // ---------------------------------------------------------------- 다른 흐름에서 쓰는 조회

    /**
     * 시즌의 회차 흐름. 기존 5화(legacy) 시즌은 V10에서 모두 지웠고 새 시즌은 장면 블록 회차뿐이라 항상 blocks다.
     * 응답 호환을 위해 필드는 남긴다.
     */
    public String formatOf(AiSeason season) {
        return FORMAT_BLOCKS;
    }

    /** 시즌 상태 응답(GET /api/ai/seasons/me)의 story. 새 흐름을 시작하지 않은 시즌이면 null. */
    public AiStoryStateView stateOf(Long seasonPk) {
        AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
        return snapshot.isStarted() ? toStateView(snapshot) : null;
    }

    /**
     * 출연진 소개 한 줄(1화 무대 소개에서 받은 description). 시즌 상태의 characters[]에 붙여, 2화 이후에도 소개를
     * 보여줄 수 있게 한다. 1화를 아직 생성하지 않았으면 빈 map, 내 빵은 값이 없다.
     */
    public Map<UUID, String> castDescriptions(Long seasonPk) {
        AiStageIntroView stageIntro = storedStageIntro(stateWriter.snapshot(seasonPk));
        Map<UUID, String> descriptions = new HashMap<>();
        if (stageIntro != null) {
            stageIntro.cast().stream()
                    .filter(c -> c.description() != null)
                    .forEach(c -> descriptions.put(c.breadId(), c.description()));
        }
        return descriptions;
    }

    /** 새 흐름 시즌의 최종 리포트(10화 완료 때 저장). 새 흐름 시즌이 아니면 empty. */
    public Optional<AiReportView> storedReport(Long seasonPk) {
        AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
        if (!snapshot.isStarted()) {
            return Optional.empty();
        }
        String json = stateWriter.findReportJson(seasonPk).orElseThrow(() -> new AiStoryConflictException(
                AiStoryConflictException.REPORT_NOT_READY, "10화를 완료하면 리포트를 볼 수 있습니다."));
        return Optional.of(AiStoryJson.read(json, AiReportView.class));
    }

    /** 개발 전용: 현재 시즌을 새 흐름 시작 전 상태로 되돌린다. */
    public void resetCurrentSeason(Long memberId) {
        stateWriter.resetStory(currentSeason(memberId).getId());
    }

    // ---------------------------------------------------------------- 내부 헬퍼

    private <T> T callWithRetry(String operation, Supplier<T> call, Consumer<T> validate) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                T response = call.get();
                validate.accept(response);
                return response;
            } catch (AiStoryInvalidResponseException | AiServiceException e) {
                last = e;
                log.warn("story {} attempt {} failed: {}", operation, attempt, e.getMessage());
            }
        }
        throw new AiStoryGenerationFailedException(operation, last);
    }

    private AiSeason currentSeason(Long memberId) {
        return seasonRepository.findFirstByMemberIdOrderBySeasonNumberDesc(memberId)
                .orElseThrow(AiSeasonNotFoundException::new);
    }

    private AiStorySnapshot startedSnapshot(AiSeason season) {
        AiStorySnapshot snapshot = stateWriter.snapshot(season.getId());
        if (!snapshot.isStarted()) {
            throw new AiStoryConflictException(AiStoryConflictException.STORY_NOT_STARTED, "1화부터 시작해주세요.");
        }
        return snapshot;
    }

    private AiStorySnapshot.Content readyContent(AiStorySnapshot snapshot, int order) {
        return snapshot.content(order)
                .filter(c -> c.status() == AiStoryContentStatus.READY)
                .orElseThrow(() -> new AiStoryConflictException(AiStoryConflictException.CONTENT_NOT_READY,
                        order + "화가 아직 준비되지 않았습니다."));
    }

    /** 나이와 성별은 함께 정해야 한다(계약 7장). 둘 중 하나라도 없으면 409 AGE_REQUIRED. */
    private User userWithAge(Long memberId) {
        User user = userRepository.findById(memberId).orElseThrow(() -> new UserNotFoundException(memberId));
        if (user.getAge() == null || user.getGender() == null) {
            throw new AiStoryConflictException(AiStoryConflictException.AGE_REQUIRED, "나이와 성별을 먼저 입력해주세요.");
        }
        return user;
    }

    private static void validateOrder(int order) {
        if (!AiStoryCatalog.isValidOrder(order)) {
            throw new AiInputValidationException("order는 1..10 범위여야 합니다.");
        }
    }

    private static AiStageIntroView storedStageIntro(AiStorySnapshot snapshot) {
        return snapshot.content(AiStoryCatalog.FIRST_ORDER)
                .map(c -> AiStoryJson.read(c.stageIntroJson(), AiStageIntroView.class))
                .orElse(null);
    }

    /** 모든 FastAPI 호출에 함께 보내는 맥락(5-0). 학습 데이터에 이 시즌 1:1 장면 피드백·좋아요를 더한다. */
    private StoryTypes.GenerationContext context(AiStorySnapshot snapshot, AiStoryContractMapper mapper, int age,
                                                 Long memberId) {
        StoryTypes.LearnedProfile base = learningDataProvider.load(memberId, snapshot.seasonNumber());
        StoryTypes.LearnedProfile learned = new StoryTypes.LearnedProfile(base.summary(), base.recentAnswers(),
                stateWriter.recentFeedbacks(snapshot.seasonPk()), stateWriter.recentLikedMyMessages(snapshot.seasonPk()));
        return new StoryTypes.GenerationContext(snapshot.setting().wireValue(), AiSeasonSettingPolicy.audience(age),
                AiSeasonSettingPolicy.ageGroup(age), mapper.castMembers(), learned);
    }

    /**
     * 회차 쪽지 발신 빵(계약 7장 "회차 쪽지 발신자"): 그 회차의 1:1 상대. 1:1 상대가 없던 회차(혼자)는 친구,
     * 10화는 연애 흐름이면 연인, 혼자 흐름이면 친구.
     */
    private static AiStoryCastMember noteSender(AiStorySnapshot snapshot, AiStorySnapshot.Content content, int order) {
        AiStoryCastMember friend = snapshot.cast().stream().filter(c -> c.castRole() == AiCastRole.FRIEND)
                .findFirst().orElseThrow();
        if (order == AiStoryCatalog.FINAL_ORDER) {
            return snapshot.romancePartnerId() != null
                    ? snapshot.member(snapshot.romancePartnerId()).orElse(friend) : friend;
        }
        return AiStoryJson.read(content.blocksJson(), BLOCK_LIST).stream()
                .filter(b -> AiSceneBlockType.PAIR.wireValue().equals(b.type()))
                .findFirst()
                .flatMap(pair -> pair.participants().stream().filter(p -> !p.isMe()).findFirst())
                .flatMap(p -> snapshot.member(p.breadId()))
                .orElse(friend);
    }

    /** 쪽지 첫 문장(마침표·물음표·느낌표·줄바꿈 전까지). */
    static String firstSentence(String body) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^.*?[.!?…\n]").matcher(body.strip());
        return (matcher.find() ? matcher.group() : body.strip()).strip();
    }

    /** 1화부터 이번 회차까지의 회차 요약(이번 회차는 방금 받은 정리 결과). */
    private static List<StoryTypes.EpisodeSummary> episodeSummaries(AiStorySnapshot snapshot, int order,
                                                                    String currentSummary) {
        List<StoryTypes.EpisodeSummary> summaries = new ArrayList<>();
        snapshot.contents().stream()
                .filter(c -> c.order() < order && c.summaryJson() != null)
                .forEach(c -> summaries.add(new StoryTypes.EpisodeSummary(c.order(),
                        String.valueOf(AiStoryJson.read(c.summaryJson(), Map.class).get("episodeSummary")))));
        summaries.add(new StoryTypes.EpisodeSummary(order, currentSummary));
        return summaries;
    }

    /** 시즌에서 이미 쓴 장면 소재: 이전 회차의 배경 장면, 상황 소개 제목, 전환 카드 문구. 같은 소재를 반복하지 않게 한다. */
    private static List<String> usedMaterials(AiStorySnapshot snapshot, int order) {
        List<String> materials = new ArrayList<>();
        snapshot.contents().stream()
                .filter(c -> c.order() < order && c.status() == AiStoryContentStatus.READY)
                .forEach(c -> {
                    materials.add(AiStoryCatalog.settingScene(snapshot.setting(), c.order()));
                    AiStoryIntroView intro = AiStoryJson.read(c.introJson(), AiStoryIntroView.class);
                    if (intro != null) {
                        materials.add(intro.title());
                    }
                    AiStoryJson.read(c.blocksJson(), BLOCK_LIST).stream()
                            .map(AiSceneBlockView::transitionText)
                            .filter(Objects::nonNull)
                            .forEach(materials::add);
                });
        return materials.stream().distinct().toList();
    }

    private static AiEpisodePlanner.Input planInput(AiStorySnapshot snapshot, int order) {
        Map<UUID, Integer> affinity = new HashMap<>();
        Map<UUID, List<StoryTypes.Thread>> openThreads = new HashMap<>();
        snapshot.relations().forEach(r -> {
            affinity.put(r.breadId(), r.affinity());
            openThreads.put(r.breadId(), r.openThreads());
        });
        Map<Integer, AiEpisodePlanner.ChoiceRecord> choices = new HashMap<>();
        snapshot.choices().forEach(c -> choices.put(c.pointId(), new AiEpisodePlanner.ChoiceRecord(c.breadId(), c.accepted())));
        return new AiEpisodePlanner.Input(order, snapshot.setting(), snapshot.route(), snapshot.romancePartnerId(),
                snapshot.cast(), affinity, openThreads, choices);
    }

    private static AiChoiceJudge.Input judgeInput(AiStorySnapshot snapshot, int pointId, UUID breadId) {
        if (breadId == null) {
            return new AiChoiceJudge.Input(pointId, null, null, 0, 0, List.of());
        }
        AiStorySnapshot.Relation relation = snapshot.relation(breadId).orElseThrow();
        AiStoryCastMember member = snapshot.member(breadId).orElseThrow();
        List<UUID> previous = snapshot.choices().stream()
                .filter(c -> c.pointId() < pointId && c.breadId() != null)
                .map(AiStorySnapshot.Choice::breadId)
                .toList();
        return new AiChoiceJudge.Input(pointId, breadId, member.castRole().attachmentStyle(), relation.affinity(),
                relation.rejectCount(), previous);
    }

    private static List<UUID> choiceCandidates(AiStorySnapshot.Content content) {
        return AiStoryJson.read(content.blocksJson(), BLOCK_LIST).stream()
                .filter(b -> b.choice() != null)
                .flatMap(b -> b.choice().candidates().stream())
                .map(AiSceneBlockView.SceneChoiceCandidate::breadId)
                .toList();
    }

    /** 설계 블록 순서대로 FastAPI가 쓴 대사를 붙여 앱 응답 블록을 만든다. 10화 인터뷰는 인터뷰 호출 결과로 채운다. */
    private static List<AiSceneBlockView> toBlockViews(AiEpisodePlan plan, Map<String, StoryTypes.GeneratedBlock> generated,
                                                       Map<String, List<StoryTypes.Message>> interviews,
                                                       AiStorySnapshot snapshot, AiStoryContractMapper mapper) {
        int[] sequence = {1};
        List<AiSceneBlockView> views = new ArrayList<>();
        for (BlockPlan blockPlan : plan.blocks()) {
            String type = blockPlan.type().wireValue();
            switch (blockPlan.type()) {
                case CHOICE -> {
                    Map<String, String> memories = new HashMap<>();
                    generated.get(blockPlan.id()).candidates().forEach(c -> memories.put(c.breadId(), c.memory()));
                    List<AiSceneBlockView.SceneChoiceCandidate> candidates = blockPlan.choice().candidateIds().stream()
                            .map(id -> new AiSceneBlockView.SceneChoiceCandidate(id,
                                    memories.getOrDefault(mapper.contractId(id), memoryLine(snapshot, id))))
                            .toList();
                    views.add(new AiSceneBlockView(blockPlan.id(), type, null, null, null, null,
                            new AiSceneBlockView.SceneChoice(blockPlan.choice().pointId(), blockPlan.choice().question(),
                                    candidates),
                            null, null));
                }
                case TRANSITION -> views.add(new AiSceneBlockView(blockPlan.id(), type, null, null,
                        generated.get(blockPlan.id()).transitionText(), null, null, null, null));
                case INTERVIEW -> views.add(new AiSceneBlockView(blockPlan.id(), type,
                        mapper.participants(blockPlan.participantIds()),
                        mapper.messageViews(interviews.get(mapper.contractId(blockPlan.focusBreadId())), sequence),
                        null, null, null, null, new AiSceneBlockView.SceneInterview(blockPlan.focusBreadId())));
                default -> {
                    AiSceneBlockView.SceneMessenger messenger = blockPlan.type() == AiSceneBlockType.MESSENGER
                            ? new AiSceneBlockView.SceneMessenger(blockPlan.messengerKind(), blockPlan.roomName()) : null;
                    views.add(new AiSceneBlockView(blockPlan.id(), type, mapper.participants(blockPlan.participantIds()),
                            mapper.messageViews(generated.get(blockPlan.id()).messages(), sequence), null, messenger,
                            null, null, null));
                }
            }
        }
        return views;
    }

    /** 선택 카드의 "기억 한 줄" 대체값: 정리 단계의 선택용 기억 → 최근 장면 → 기본 문구 순. */
    private static String memoryLine(AiStorySnapshot snapshot, UUID breadId) {
        return snapshot.relation(breadId)
                .map(r -> r.choiceMemory() != null ? r.choiceMemory()
                        : !r.recentMemories().isEmpty() ? r.recentMemories().get(r.recentMemories().size() - 1)
                        : DEFAULT_MEMORY)
                .orElse(DEFAULT_MEMORY);
    }

    private AiEpisodeContentView toContentView(AiStorySnapshot snapshot, AiStorySnapshot.Content content) {
        String status = content.status().name().toLowerCase();
        if (content.status() != AiStoryContentStatus.READY) {
            return new AiEpisodeContentView(content.order(), status, content.errorCode(), null, null, null,
                    content.completed(), List.of(), List.of());
        }
        List<AiStoryFeedbackView> feedbacks = stateWriter.findFeedbacks(snapshot.seasonPk(), content.order()).stream()
                .map(f -> new AiStoryFeedbackView(f.getEpisodeOrder(), f.getBlockId(), f.getMessageId(), f.getText()))
                .toList();
        return new AiEpisodeContentView(content.order(), status, null,
                AiStoryJson.read(content.introJson(), AiStoryIntroView.class),
                AiStoryJson.read(content.stageIntroJson(), AiStageIntroView.class),
                blocksWithChoiceResult(snapshot, content), content.completed(), feedbacks,
                stateWriter.findLikedMessageIds(snapshot.seasonPk(), content.order()));
    }

    /**
     * 회차 블록 + (선택을 마쳤으면) 선택 결과 블록. choice_result와 거절 뒤 대체 pair를 choice 블록 바로 뒤에 넣어,
     * 앱을 다시 켜거나 다시 볼 때도 choices 응답과 같은 순서로 재생되게 한다.
     */
    private static List<AiSceneBlockView> blocksWithChoiceResult(AiStorySnapshot snapshot,
                                                                 AiStorySnapshot.Content content) {
        List<AiSceneBlockView> blocks = new ArrayList<>(AiStoryJson.read(content.blocksJson(), BLOCK_LIST));
        Integer pointId = AiStoryCatalog.pointOf(content.order());
        if (pointId == null) {
            return blocks;
        }
        snapshot.choice(pointId).ifPresent(choice -> {
            int choiceIndex = 0;
            for (int i = 0; i < blocks.size(); i++) {
                if (blocks.get(i).choice() != null) {
                    choiceIndex = i;
                }
            }
            blocks.addAll(choiceIndex + 1, AiStoryJson.read(choice.resultBlocksJson(), BLOCK_LIST));
        });
        return blocks;
    }

    private AiStoryStateView toStateView(AiStorySnapshot snapshot) {
        List<AiStoryStateView.ChoiceHistory> choices = snapshot.choices().stream()
                .map(c -> new AiStoryStateView.ChoiceHistory(c.pointId(), c.episodeOrder(), c.breadId(), c.accepted()))
                .toList();
        List<AiStoryStateView.BreadStatus> breads = snapshot.cast().stream()
                .filter(m -> !m.isMe())
                .map(m -> new AiStoryStateView.BreadStatus(m.breadId(), m.name(), m.castRole().wireValue(),
                        snapshot.relation(m.breadId()).map(AiStorySnapshot.Relation::statusText).orElse(null)))
                .toList();
        List<Integer> completedOrders = snapshot.contents().stream()
                .filter(AiStorySnapshot.Content::completed)
                .map(AiStorySnapshot.Content::order)
                .sorted()
                .toList();
        java.util.TreeSet<Integer> unlocked = new java.util.TreeSet<>(snapshot.unlockedOrders());
        for (int order = AiStoryCatalog.FIRST_ORDER; order <= AiStoryCatalog.LAST_FREE_ORDER; order++) {
            unlocked.add(order);
        }
        snapshot.contents().stream().filter(c -> c.status() == AiStoryContentStatus.READY)
                .forEach(c -> unlocked.add(c.order()));
        return new AiStoryStateView(snapshot.setting().wireValue(), snapshot.currentOrder(), completedOrders,
                snapshot.route() == null ? null : snapshot.route().wireValue(), snapshot.romancePartnerId(),
                choices, breads, List.copyOf(unlocked), properties.episodeTokenCost());
    }

    /**
     * 최종 상대(finalPartnerId)는 Spring이 흐름으로 정한다: 연애 흐름이면 연인, 혼자 흐름이면 null. AI 응답의 값은 쓰지
     * 않는다(앱은 null이면 상대 없이 내 빵만 굽는 연출).
     */
    private static AiReportView toReportView(SeasonReportResponse report, AiStorySnapshot snapshot) {
        AiReportHighlightsView highlights = new AiReportHighlightsView(
                new AiHighlightView(report.highlights().episode2().message(), report.highlights().episode2().reason()),
                new AiHighlightView(report.highlights().episode4().message(), report.highlights().episode4().reason()));
        UUID finalPartnerId = snapshot.route() == AiStoryRoute.ROMANCE ? snapshot.romancePartnerId() : null;
        return new AiReportView(finalPartnerId, report.tendency(), report.feedback(),
                report.strengths(), report.weaknesses(), highlights, report.renderMode());
    }
}
