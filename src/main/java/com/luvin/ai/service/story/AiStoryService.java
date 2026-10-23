package com.luvin.ai.service.story;

import com.fasterxml.jackson.core.type.TypeReference;
import com.luvin.ai.client.dto.LetterResponseDto;
import com.luvin.ai.client.dto.ReportResponseDto;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.client.story.AiStoryClient;
import com.luvin.ai.client.story.ChoiceResultRequestDto;
import com.luvin.ai.client.story.EpisodeNoteRequestDto;
import com.luvin.ai.client.story.EpisodeWriteRequestDto;
import com.luvin.ai.client.story.EpisodeWriteResponseDto;
import com.luvin.ai.client.story.InterviewRequestDto;
import com.luvin.ai.client.story.SeasonReportRequestDto;
import com.luvin.ai.client.story.StageIntroRequestDto;
import com.luvin.ai.client.story.StageIntroResponseDto;
import com.luvin.ai.client.story.StoryBlockPlanDto;
import com.luvin.ai.client.story.StoryBlocksResponseDto;
import com.luvin.ai.client.story.StoryContextDto;
import com.luvin.ai.client.story.StoryWrittenBlockDto;
import com.luvin.ai.client.story.SummarizeRequestDto;
import com.luvin.ai.client.story.SummarizeResponseDto;
import com.luvin.ai.config.AiStoryProperties;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonStatus;
import com.luvin.ai.domain.AiStoryContentStatus;
import com.luvin.ai.dto.AiHighlightView;
import com.luvin.ai.dto.AiReportHighlightsView;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.story.AiChoiceOutcomeView;
import com.luvin.ai.dto.story.AiEpisodeContentView;
import com.luvin.ai.dto.story.AiSceneBlockView;
import com.luvin.ai.dto.story.AiStageIntroView;
import com.luvin.ai.dto.story.AiStoryIntroView;
import com.luvin.ai.dto.story.AiStoryMessageView;
import com.luvin.ai.dto.story.AiStoryParticipantView;
import com.luvin.ai.dto.story.AiStoryStateView;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 장면 블록 회차(1~10화) 흐름. 설계·판정은 코드({@link AiEpisodePlanner}, {@link AiChoiceJudge})로 하고,
 * 대사·정리·쪽지·리포트는 FastAPI({@link AiStoryClient})에 맡긴다. Spring은 Gemini를 직접 부르지 않는다.
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
    public static final String FORMAT_LEGACY = "legacy";
    public static final String FORMAT_BLOCKS = "blocks";
    private static final TypeReference<List<AiSceneBlockView>> BLOCK_LIST = new TypeReference<>() {
    };

    private final AiStoryStateWriter stateWriter;
    private final AiStoryClient storyClient;
    private final AiSeasonRepository seasonRepository;
    private final AiEpisodeProgressRepository episodeProgressRepository;
    private final UserRepository userRepository;
    private final OwnerSubjectProvider ownerSubjectProvider;
    private final AiLearningDataProvider learningDataProvider;
    private final AiEpisodeReleasePolicy releasePolicy;
    private final AiStoryProperties properties;
    private final ExecutorService executor;

    public AiStoryService(AiStoryStateWriter stateWriter, AiStoryClient storyClient,
                          AiSeasonRepository seasonRepository, AiEpisodeProgressRepository episodeProgressRepository,
                          UserRepository userRepository,
                          OwnerSubjectProvider ownerSubjectProvider, AiLearningDataProvider learningDataProvider,
                          AiEpisodeReleasePolicy releasePolicy, AiStoryProperties properties,
                          @Qualifier("aiStoryExecutor") ExecutorService executor) {
        this.stateWriter = stateWriter;
        this.storyClient = storyClient;
        this.seasonRepository = seasonRepository;
        this.episodeProgressRepository = episodeProgressRepository;
        this.userRepository = userRepository;
        this.ownerSubjectProvider = ownerSubjectProvider;
        this.learningDataProvider = learningDataProvider;
        this.releasePolicy = releasePolicy;
        this.properties = properties;
        this.executor = executor;
    }

    // ---------------------------------------------------------------- 회차 콘텐츠

    /**
     * 저장된 회차가 있으면 그대로, 없으면 생성을 접수하고 generating으로 응답한다. 새 흐름을 아직 시작하지 않은 시즌은
     * 1화 요청에서 시작한다(나이로 배경 결정, 출연진 배정) — 나이가 없으면 409 AGE_REQUIRED.
     */
    public AiEpisodeContentView getContent(Long memberId, int order) {
        validateOrder(order);
        AiSeason season = currentSeason(memberId);
        AiStorySnapshot snapshot = stateWriter.snapshot(season.getId());

        if (!snapshot.isStarted()) {
            if (season.getStatus() == AiSeasonStatus.COMPLETED) {
                throw new AiSeasonCompletedException();
            }
            assertNotLegacy(season);
            if (order != AiStoryCatalog.FIRST_ORDER) {
                throw new AiStoryConflictException(AiStoryConflictException.STORY_NOT_STARTED, "1화부터 시작해주세요.");
            }
            int age = requireAge(memberId);
            stateWriter.startStory(season.getId(),
                    AiSeasonSettingPolicy.decideSetting(age, ThreadLocalRandom.current()));
            snapshot = stateWriter.snapshot(season.getId());
        }

        Optional<AiStorySnapshot.Content> existing = snapshot.content(order);
        if (existing.isPresent() && existing.get().status() == AiStoryContentStatus.READY) {
            return toContentView(existing.get());
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
        return toContentView(stateWriter.snapshot(season.getId()).content(order).orElseThrow());
    }

    /** 백그라운드 생성. 어떤 오류든 FAILED로 남겨 다음 GET content가 다시 시도하게 한다(스레드 밖으로 던지지 않는다). */
    void generate(Long memberId, Long seasonPk, int order, Long contentPk) {
        try {
            AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
            String ownerSubject = ownerSubjectProvider.resolve(memberId);
            int age = requireAge(memberId);

            AiStageIntroView stageIntro = order == AiStoryCatalog.FIRST_ORDER
                    ? requestStageIntro(ownerSubject, snapshot, age, memberId)
                    : snapshot.content(AiStoryCatalog.FIRST_ORDER)
                            .map(c -> AiStoryJson.read(c.stageIntroJson(), AiStageIntroView.class)).orElse(null);
            StoryContextDto context = context(snapshot, age, stageIntro, memberId);

            AiEpisodePlan plan = AiEpisodePlanner.plan(planInput(snapshot, order));
            EpisodeWriteRequestDto request = new EpisodeWriteRequestDto(context, order, plan.commonEvent(),
                    plan.settingScene(), plan.route() == null ? null : plan.route().wireValue(),
                    plan.referenceEpisode(), plan.openThreads(), toPlanDtos(plan.episodeBlocks()));
            EpisodeWriteResponseDto episode = callWithRetry("episode",
                    () -> storyClient.writeEpisode(ownerSubject, request),
                    r -> AiStoryResponseValidator.validateEpisode(r, plan.episodeBlocks()));

            Map<String, StoryWrittenBlockDto> written = new HashMap<>();
            episode.blocks().forEach(b -> written.put(b.id(), b));
            if (!plan.interviewBlocks().isEmpty()) {
                InterviewRequestDto interviewRequest = new InterviewRequestDto(context, plan.settingScene(),
                        toPlanDtos(plan.interviewBlocks()));
                StoryBlocksResponseDto interviews = callWithRetry("interview",
                        () -> storyClient.writeInterview(ownerSubject, interviewRequest),
                        r -> AiStoryResponseValidator.validateBlocks(r.blocks(), plan.interviewBlocks()));
                interviews.blocks().forEach(b -> written.put(b.id(), b));
            }

            List<AiSceneBlockView> blocks = toBlockViews(plan.blocks(), written, snapshot, null);
            AiStoryIntroView intro = new AiStoryIntroView(episode.intro().title(), episode.intro().situation());
            stateWriter.saveGenerated(contentPk, AiStoryJson.write(intro),
                    order == AiStoryCatalog.FIRST_ORDER ? AiStoryJson.write(stageIntro) : null,
                    AiStoryJson.write(blocks));
        } catch (Exception e) {
            log.warn("story episode generation failed: seasonPk={}, order={}, cause={}",
                    seasonPk, order, e.getClass().getSimpleName());
            stateWriter.markGenerationFailed(contentPk, AiStoryGenerationFailedException.CODE);
        }
    }

    private AiStageIntroView requestStageIntro(String ownerSubject, AiStorySnapshot snapshot, int age, Long memberId) {
        StoryContextDto context = context(snapshot, age, null, memberId);
        StageIntroRequestDto request = new StageIntroRequestDto(context, snapshot.setting().title(),
                snapshot.setting().subtitle());
        List<UUID> castIds = snapshot.cast().stream().map(AiStoryCastMember::breadId).toList();
        StageIntroResponseDto response = callWithRetry("stage-intro",
                () -> storyClient.stageIntro(ownerSubject, request),
                r -> AiStoryResponseValidator.validateStageIntro(r, castIds));
        Map<UUID, StageIntroResponseDto.CastIntro> byId = response.cast().stream()
                .collect(Collectors.toMap(StageIntroResponseDto.CastIntro::breadId, c -> c, (a, b) -> a));
        List<AiStageIntroView.StageCast> cast = snapshot.cast().stream()
                .map(m -> new AiStageIntroView.StageCast(m.breadId(), m.name(),
                        byId.get(m.breadId()).role(), byId.get(m.breadId()).description()))
                .toList();
        return new AiStageIntroView(snapshot.setting().wireValue(), snapshot.setting().title(),
                snapshot.setting().subtitle(), cast);
    }

    // ---------------------------------------------------------------- 선택

    /**
     * 3·5·6·8화 끝 선택. 판정은 코드 규칙으로 하고, 결과 장면만 FastAPI가 쓴다. 같은 선택을 다시 보내면 저장된 결과를
     * 그대로 돌려주고, 다른 빵으로 다시 고르려 하면 409 CHOICE_ALREADY_MADE.
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
        List<BlockPlan> resultPlan = AiEpisodePlanner.planChoiceResult(pointId, breadId, decision, snapshot.cast());
        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        StoryContextDto context = context(snapshot, requireAge(memberId), storedStageIntro(snapshot), memberId);
        ChoiceResultRequestDto request = new ChoiceResultRequestDto(context, order, pointId, breadId,
                decision.accepted(), decision.reasonCode(), decision.shortReply(),
                AiStoryCatalog.settingScene(snapshot.setting(), order), toPlanDtos(resultPlan));
        StoryBlocksResponseDto response = callWithRetry("choice-result",
                () -> storyClient.writeChoiceResult(ownerSubject, request),
                r -> AiStoryResponseValidator.validateBlocks(r.blocks(), resultPlan));

        Map<String, StoryWrittenBlockDto> written = response.blocks().stream()
                .collect(Collectors.toMap(StoryWrittenBlockDto::id, b -> b));
        AiSceneBlockView.SceneChoiceResult result = new AiSceneBlockView.SceneChoiceResult(pointId, breadId, decision.accepted());
        List<AiSceneBlockView> blocks = toBlockViews(resultPlan, written, snapshot, result);

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
        if (!java.util.Objects.equals(choice.breadId(), requestedBreadId)) {
            throw new AiStoryConflictException(AiStoryConflictException.CHOICE_ALREADY_MADE, "이미 선택을 마쳤습니다.");
        }
        return new AiChoiceOutcomeView(AiStoryJson.read(choice.resultBlocksJson(), BLOCK_LIST), toStateView(snapshot));
    }

    // ---------------------------------------------------------------- 회차 완료

    /**
     * 회차 완료: 정리 단계(관계 기록 갱신안·호감 변화 제안) → 회차 쪽지 → (10화면) 최종 리포트를 FastAPI로 만들고
     * 한 트랜잭션에 저장한다. 하나라도 실패하면 아무것도 저장하지 않는다. 이미 완료한 회차면 상태만 돌려준다.
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
        StoryContextDto context = context(snapshot, requireAge(memberId), storedStageIntro(snapshot), memberId);
        List<UUID> relationIds = snapshot.relations().stream().map(AiStorySnapshot.Relation::breadId).toList();
        List<UUID> castIds = snapshot.cast().stream().map(AiStoryCastMember::breadId).toList();

        SummarizeRequestDto summarizeRequest = new SummarizeRequestDto(context, order, script(snapshot, content, pointId));
        SummarizeResponseDto summary = callWithRetry("summarize",
                () -> storyClient.summarize(ownerSubject, summarizeRequest),
                r -> AiStoryResponseValidator.validateSummary(r, relationIds));

        boolean finale = order == AiStoryCatalog.FINAL_ORDER;
        EpisodeNoteRequestDto noteRequest = new EpisodeNoteRequestDto(context, order, summary.episodeSummary(), finale);
        LetterResponseDto note = callWithRetry("episode-note",
                () -> storyClient.writeEpisodeNote(ownerSubject, noteRequest),
                r -> AiStoryResponseValidator.validateNote(r, castIds));

        String reportJson = null;
        if (finale) {
            List<SeasonReportRequestDto.EpisodeSummary> summaries = new ArrayList<>();
            snapshot.contents().stream()
                    .filter(c -> c.order() < order && c.summaryJson() != null)
                    .forEach(c -> summaries.add(new SeasonReportRequestDto.EpisodeSummary(c.order(),
                            String.valueOf(AiStoryJson.read(c.summaryJson(), Map.class).get("episodeSummary")))));
            summaries.add(new SeasonReportRequestDto.EpisodeSummary(order, summary.episodeSummary()));
            SeasonReportRequestDto reportRequest = new SeasonReportRequestDto(context,
                    snapshot.route() == null ? null : snapshot.route().wireValue(), snapshot.romancePartnerId(),
                    summaries);
            ReportResponseDto report = callWithRetry("season-report",
                    () -> storyClient.writeSeasonReport(ownerSubject, reportRequest),
                    AiStoryResponseValidator::validateReport);
            reportJson = AiStoryJson.write(toReportView(report));
        }

        stateWriter.completeEpisode(season.getId(), order, summary, note, reportJson);
        return toStateView(stateWriter.snapshot(season.getId()));
    }

    // ---------------------------------------------------------------- 다른 흐름에서 쓰는 조회

    /**
     * 시즌이 어느 회차 흐름인지. 새 흐름을 시작했으면 blocks, 기존 5화 흐름으로 회차 생성을 요청한 적이 있으면
     * legacy, 둘 다 아니면(새 시즌) blocks다 — 앱은 이 값으로 회차 화면과 API를 고른다.
     */
    public String formatOf(AiSeason season) {
        if (season.isStoryStarted()) {
            return FORMAT_BLOCKS;
        }
        return episodeProgressRepository.existsBySeason_Id(season.getId()) ? FORMAT_LEGACY : FORMAT_BLOCKS;
    }

    /** 시즌 상태 응답(GET /api/ai/seasons/me)의 story. 새 흐름을 시작하지 않은 시즌이면 null. */
    public AiStoryStateView stateOf(Long seasonPk) {
        AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
        return snapshot.isStarted() ? toStateView(snapshot) : null;
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
            assertNotLegacy(season);
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

    /** 기존 5화 흐름으로 진행 중인 시즌을 새 흐름으로 섞어 진행하지 않는다. */
    private void assertNotLegacy(AiSeason season) {
        if (FORMAT_LEGACY.equals(formatOf(season))) {
            throw new AiStoryConflictException(AiStoryConflictException.LEGACY_SEASON,
                    "기존 5화 구성으로 진행 중인 시즌입니다. 다음 시즌부터 10화 구성으로 진행됩니다.");
        }
    }

    private int requireAge(Long memberId) {
        User user = userRepository.findById(memberId).orElseThrow(() -> new UserNotFoundException(memberId));
        if (user.getAge() == null) {
            throw new AiStoryConflictException(AiStoryConflictException.AGE_REQUIRED, "나이를 먼저 입력해주세요.");
        }
        return user.getAge();
    }

    private static void validateOrder(int order) {
        if (!AiStoryCatalog.isValidOrder(order)) {
            throw new AiInputValidationException("order는 1..10 범위여야 합니다.");
        }
    }

    private AiStageIntroView storedStageIntro(AiStorySnapshot snapshot) {
        return snapshot.content(AiStoryCatalog.FIRST_ORDER)
                .map(c -> AiStoryJson.read(c.stageIntroJson(), AiStageIntroView.class))
                .orElse(null);
    }

    private StoryContextDto context(AiStorySnapshot snapshot, int age, AiStageIntroView stageIntro, Long memberId) {
        Map<UUID, AiStageIntroView.StageCast> intros = new HashMap<>();
        if (stageIntro != null) {
            stageIntro.cast().forEach(c -> intros.put(c.breadId(), c));
        }
        List<StoryContextDto.CastMember> cast = snapshot.cast().stream()
                .map(m -> new StoryContextDto.CastMember(m.breadId(), m.name(), m.castRole().wireValue(),
                        m.castRole().attachmentStyle().wireValue(), m.gender(), m.isMe(),
                        intros.containsKey(m.breadId()) ? intros.get(m.breadId()).role() : null,
                        intros.containsKey(m.breadId()) ? intros.get(m.breadId()).description() : null))
                .toList();
        List<StoryContextDto.Relation> relations = snapshot.relations().stream()
                .map(r -> new StoryContextDto.Relation(r.breadId(), r.affinity(), r.rejectCount(), r.statusText(),
                        r.memorySummary(), r.recentMemories(), r.knownFacts(), r.openThreads()))
                .toList();
        List<StoryContextDto.ChoiceRecord> choices = snapshot.choices().stream()
                .map(c -> new StoryContextDto.ChoiceRecord(c.pointId(), c.episodeOrder(), c.breadId(), c.accepted(),
                        c.reasonCode()))
                .toList();
        return new StoryContextDto(snapshot.setting().wireValue(), AiSeasonSettingPolicy.audience(age),
                AiSeasonSettingPolicy.ageBand(age), age, cast, relations, choices,
                learningDataProvider.load(memberId, snapshot.seasonNumber()));
    }

    private static AiEpisodePlanner.Input planInput(AiStorySnapshot snapshot, int order) {
        Map<UUID, Integer> affinity = new HashMap<>();
        Map<UUID, List<String>> openThreads = new HashMap<>();
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

    /** 정리 단계 입력: 그 회차에서 재생된 대사(회차 블록 + 선택 결과 장면) 순서대로. */
    private static List<SummarizeRequestDto.ScriptLine> script(AiStorySnapshot snapshot,
                                                              AiStorySnapshot.Content content, Integer pointId) {
        List<AiSceneBlockView> blocks = new ArrayList<>(AiStoryJson.read(content.blocksJson(), BLOCK_LIST));
        if (pointId != null) {
            snapshot.choice(pointId).ifPresent(c -> blocks.addAll(AiStoryJson.read(c.resultBlocksJson(), BLOCK_LIST)));
        }
        Map<UUID, String> names = snapshot.cast().stream()
                .collect(Collectors.toMap(AiStoryCastMember::breadId, AiStoryCastMember::name));
        List<SummarizeRequestDto.ScriptLine> lines = new ArrayList<>();
        for (AiSceneBlockView block : blocks) {
            if (block.messages() == null) {
                continue;
            }
            for (AiStoryMessageView message : block.messages()) {
                if (Boolean.TRUE.equals(message.deletedAfterSend())) {
                    continue;
                }
                lines.add(new SummarizeRequestDto.ScriptLine(block.id(), block.type(), message.speakerId(),
                        names.get(message.speakerId()), message.text()));
            }
        }
        return lines;
    }

    private static List<StoryBlockPlanDto> toPlanDtos(List<BlockPlan> plans) {
        return plans.stream()
                .map(p -> new StoryBlockPlanDto(p.id(), p.type().wireValue(), p.participantIds(), p.messengerKind(),
                        p.focusBreadId(), p.shortReply()))
                .toList();
    }

    /** 설계 블록 순서대로 AI가 쓴 대사를 붙여 앱 응답 블록을 만든다. choice 블록은 관계 기록으로 Spring이 채운다. */
    private static List<AiSceneBlockView> toBlockViews(List<BlockPlan> plans, Map<String, StoryWrittenBlockDto> written,
                                                       AiStorySnapshot snapshot,
                                                       AiSceneBlockView.SceneChoiceResult choiceResult) {
        UUID me = snapshot.me().breadId();
        int sequence = 1;
        List<AiSceneBlockView> views = new ArrayList<>();
        for (BlockPlan plan : plans) {
            if (plan.type() == AiSceneBlockType.CHOICE) {
                List<AiSceneBlockView.SceneChoiceCandidate> candidates = plan.choice().candidateIds().stream()
                        .map(id -> new AiSceneBlockView.SceneChoiceCandidate(id, memoryLine(snapshot, id)))
                        .toList();
                views.add(new AiSceneBlockView(plan.id(), plan.type().wireValue(), null, null, null, null,
                        new AiSceneBlockView.SceneChoice(plan.choice().pointId(), plan.choice().question(), candidates),
                        null, null));
                continue;
            }
            StoryWrittenBlockDto block = written.get(plan.id());
            if (plan.type() == AiSceneBlockType.TRANSITION) {
                views.add(new AiSceneBlockView(plan.id(), plan.type().wireValue(), null, null, block.transitionText(),
                        null, null, null, null));
                continue;
            }
            List<AiStoryMessageView> messages = new ArrayList<>();
            for (StoryWrittenBlockDto.Message m : block.messages()) {
                messages.add(new AiStoryMessageView(UUID.randomUUID(), sequence++, m.speakerId(),
                        me.equals(m.speakerId()), m.text(), m.read(), m.typing(), m.deletedAfterSend()));
            }
            List<AiStoryParticipantView> participants = plan.participantIds().stream()
                    .map(id -> participant(snapshot, id))
                    .toList();
            AiSceneBlockView.SceneMessenger messenger = plan.type() == AiSceneBlockType.MESSENGER
                    ? new AiSceneBlockView.SceneMessenger(plan.messengerKind(), block.roomName()) : null;
            AiSceneBlockView.SceneInterview interview = plan.type() == AiSceneBlockType.INTERVIEW
                    ? new AiSceneBlockView.SceneInterview(plan.focusBreadId()) : null;
            AiSceneBlockView.SceneChoiceResult result = plan.type() == AiSceneBlockType.CHOICE_RESULT ? choiceResult : null;
            views.add(new AiSceneBlockView(plan.id(), plan.type().wireValue(), participants, messages, null,
                    messenger, null, result, interview));
        }
        return views;
    }

    /** 선택 카드의 "기억 한 줄": 최근 회차 주요 장면 → 관계 요약 → 기본 문구 순. */
    private static String memoryLine(AiStorySnapshot snapshot, UUID breadId) {
        return snapshot.relation(breadId)
                .map(r -> !r.recentMemories().isEmpty() ? r.recentMemories().get(r.recentMemories().size() - 1)
                        : r.memorySummary() != null ? r.memorySummary() : DEFAULT_MEMORY)
                .orElse(DEFAULT_MEMORY);
    }

    private static AiStoryParticipantView participant(AiStorySnapshot snapshot, UUID breadId) {
        AiStoryCastMember member = snapshot.member(breadId).orElseThrow();
        return new AiStoryParticipantView(breadId, member.name(), member.castRole().wireValue(),
                member.castRole().attachmentStyle().wireValue(), member.isMe());
    }

    private static AiEpisodeContentView toContentView(AiStorySnapshot.Content content) {
        String status = content.status().name().toLowerCase();
        if (content.status() != AiStoryContentStatus.READY) {
            return new AiEpisodeContentView(content.order(), status, content.errorCode(), null, null, null,
                    content.completed());
        }
        return new AiEpisodeContentView(content.order(), status, null,
                AiStoryJson.read(content.introJson(), AiStoryIntroView.class),
                AiStoryJson.read(content.stageIntroJson(), AiStageIntroView.class),
                AiStoryJson.read(content.blocksJson(), BLOCK_LIST), content.completed());
    }

    private static AiStoryStateView toStateView(AiStorySnapshot snapshot) {
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
        return new AiStoryStateView(snapshot.setting().wireValue(), snapshot.currentOrder(), completedOrders,
                snapshot.route() == null ? null : snapshot.route().wireValue(), snapshot.romancePartnerId(),
                choices, breads);
    }

    private static AiReportView toReportView(ReportResponseDto report) {
        AiReportHighlightsView highlights = new AiReportHighlightsView(
                new AiHighlightView(report.highlights().episode2().message(), report.highlights().episode2().reason()),
                new AiHighlightView(report.highlights().episode4().message(), report.highlights().episode4().reason()));
        return new AiReportView(report.finalPartnerId(), report.tendency(), report.feedback(), report.strengths(),
                report.weaknesses(), highlights, report.renderMode());
    }
}
