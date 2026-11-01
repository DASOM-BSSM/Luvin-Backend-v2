package com.luvin.ai.service.story;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.client.story.AiStoryClient;
import com.luvin.ai.client.story.AiStoryFeatureUnsupportedException;
import com.luvin.ai.client.story.AiStreamResumeRejectedException;
import com.luvin.ai.client.story.ChoiceResultRequest;
import com.luvin.ai.client.story.ChoiceResultResponse;
import com.luvin.ai.client.story.EpisodeNoteRequest;
import com.luvin.ai.client.story.EpisodeNoteResponse;
import com.luvin.ai.client.story.EpisodePartial;
import com.luvin.ai.client.story.EpisodeRequest;
import com.luvin.ai.client.story.EpisodeResponse;
import com.luvin.ai.client.story.GenerationCall;
import com.luvin.ai.client.story.InterviewRequest;
import com.luvin.ai.client.story.InterviewResponse;
import com.luvin.ai.client.story.PreviewNoteRequest;
import com.luvin.ai.client.story.SeasonDesignRequest;
import com.luvin.ai.client.story.SeasonDesignResponse;
import com.luvin.ai.client.story.SeasonReportRequest;
import com.luvin.ai.client.story.SeasonReportResponse;
import com.luvin.ai.client.story.StageIntroRequest;
import com.luvin.ai.client.story.StageIntroResponse;
import com.luvin.ai.client.story.StoryTypes;
import com.luvin.ai.client.story.SummarizeRequest;
import com.luvin.ai.client.story.SummarizeResponse;
import com.luvin.ai.config.AiStoryConfig;
import com.luvin.ai.config.AiStoryProperties;
import com.luvin.ai.domain.AiCastRole;
import com.luvin.ai.domain.AiJobStatus;
import com.luvin.ai.domain.AiSceneBlockType;
import com.luvin.ai.domain.AiSeason;
import com.luvin.ai.domain.AiSeasonSetting;
import com.luvin.ai.domain.AiSeasonStatus;
import com.luvin.ai.domain.AiStoryContentStatus;
import com.luvin.ai.domain.AiStoryFeedback;
import com.luvin.ai.domain.AiStoryRoute;
import com.luvin.ai.dto.AiHighlightView;
import com.luvin.ai.dto.AiMessageLikeView;
import com.luvin.ai.dto.AiReportHighlightsView;
import com.luvin.ai.dto.AiReportView;
import com.luvin.ai.dto.story.AiChoiceOutcomeView;
import com.luvin.ai.dto.story.AiChoiceStatusView;
import com.luvin.ai.dto.story.AiEpisodeContentView;
import com.luvin.ai.dto.story.AiEpisodeUnlockView;
import com.luvin.ai.dto.story.AiGenerationProgressView;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;

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
    /** 미리 만든 쪽지의 표시 방식(AI 응답에 없으면). */
    static final String NOTE_RENDER_MODE = "default";
    private static final int MAX_ATTEMPTS = 2;
    /** 회차 완료 저장이 낙관적 락 충돌로 실패하면 FastAPI를 다시 부르지 않고 저장만 다시 하는 횟수. */
    private static final int SAVE_ATTEMPTS = 3;
    /** 회차 완료 한 번에 부르는 FastAPI 생성 호출 수의 최대값(정리·쪽지·10화 최종 리포트). */
    private static final int COMPLETION_CALLS = 3;
    private static final String DEFAULT_MEMORY = "아직 함께한 기억이 없어요";
    /** 본문 입력 usedMaterials 최대 개수(계약). */
    static final int USED_MATERIALS_MAX = 100;
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
            ticket = beginGeneration(season.getId(), order, order >= 7 ? snapshot.route() : null);
        } catch (ObjectOptimisticLockingFailureException concurrentRestart) {
            ticket = null; // 다른 요청이 먼저 다시 시작했다 — 그쪽 생성 상태를 그대로 보여준다.
        }
        if (ticket != null && ticket.started()) {
            Long contentPk = ticket.contentPk();
            Long seasonPk = season.getId();
            int round = ticket.round();
            executor.execute(() -> generate(memberId, seasonPk, order, contentPk, round));
        }
        AiStorySnapshot latest = stateWriter.snapshot(season.getId());
        return toContentView(latest, latest.content(order).orElseThrow());
    }

    /**
     * 새 시즌의 회차 흐름을 시작한다: 배경 결정(시즌 동안 고정), 출연진 배정·이름·성별·후보 순서. 시즌 생성 직후
     * 부른다. 배경은 첫 시즌이면 나이로, 그 뒤로는 지난 시즌들과 다르게 고른다({@link AiSeasonSettingPolicy#decideNextSetting}).
     * 이미 시작한 시즌이면 아무것도 하지 않는다. 나이가 없으면 409 AGE_REQUIRED.
     */
    public void startStoryForNewSeason(Long memberId, Long seasonPk) {
        User user = userWithAge(memberId);
        List<AiSeasonSetting> previousSettings = seasonRepository.findAllByMemberIdOrderBySeasonNumberDesc(memberId)
                .stream()
                .filter(s -> !s.getId().equals(seasonPk))
                .map(AiSeason::getSetting)
                .toList();
        stateWriter.startStory(seasonPk,
                AiSeasonSettingPolicy.decideNextSetting(user.getAge(), previousSettings, ThreadLocalRandom.current()),
                user.getNickname() != null ? user.getNickname() : user.getName(),
                breadCatalog.breadLabel(user.getPersonalityType()), user.getGender(),
                breadCatalog.allBreadLabels(), ThreadLocalRandom.current());
    }

    /**
     * 다음 회차 미리 생성: 앱이 GET content로 들어오기 전에 서버에서 생성을 시작한다. 2·3화는 이전 회차 완료가 저장된 직후,
     * 4~10화는 회차를 연(unlock) 직후에 부른다 — 열지 않은 회차는 토큰을 쓰지 않은 사용자 몫까지 만들게 되므로 시작하지
     * 않는다. GET content와 같은 조건(이전 회차 완료·열림·공개·시즌 진행 중)일 때만, 아직 회차 row가 없을 때만 시작한다
     * (생성 잠금은 GET content와 같아 겹쳐도 한 번만 생성한다). 어떤 오류도 호출부로 던지지 않는다.
     */
    void prefetch(Long memberId, Long seasonPk, int order) {
        if (!properties.prefetchNextEpisode() || order > AiStoryCatalog.FINAL_ORDER) {
            return;
        }
        try {
            AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
            AiSeason season = seasonRepository.findById(seasonPk).orElse(null);
            boolean ready = season != null && snapshot.isStarted() && snapshot.status() != AiSeasonStatus.COMPLETED
                    && snapshot.content(order).isEmpty()
                    && snapshot.content(order - 1).map(AiStorySnapshot.Content::completed).orElse(false)
                    && (AiStoryCatalog.isFree(order) || snapshot.unlockedOrders().contains(order))
                    && releasePolicy.isReleased(season, order);
            if (!ready) {
                return;
            }
            AiStoryStateWriter.GenerationTicket ticket = beginGeneration(seasonPk, order,
                    order >= 7 ? snapshot.route() : null);
            if (ticket.started()) {
                log.info("story episode prefetch started: seasonPk={}, order={}", seasonPk, order);
                generate(memberId, seasonPk, order, ticket.contentPk(), ticket.round(), true);
            }
        } catch (Exception e) {
            log.warn("story episode prefetch skipped: seasonPk={}, order={}, cause={}",
                    seasonPk, order, e.getClass().getSimpleName());
        }
    }

    /** 동시에 처음 접수해 회차 row 만들기에서 졌으면(unique 제약) 새 트랜잭션으로 다시 읽어 이긴 쪽 row를 쓴다. */
    private AiStoryStateWriter.GenerationTicket beginGeneration(Long seasonPk, int order, AiStoryRoute route) {
        try {
            return stateWriter.beginGeneration(seasonPk, order, route, staleBefore());
        } catch (DataIntegrityViolationException raceLoser) {
            return stateWriter.beginGeneration(seasonPk, order, route, staleBefore());
        }
    }

    // ---------------------------------------------------------------- 회차 쪽지 미리 만들기

    /**
     * 1~9화 쪽지를 회차를 끝내기 전에 만든다(AI episode-note-preview). 선택이 없는 회차는 본문이 준비된 뒤, 선택 회차는
     * 선택 결과 장면이 준비된 뒤(혼자 있기 포함) 부른다. 정리 단계 결과 없이 이번 회차 블록과 이전 회차까지의 관계 기록으로
     * 쓴다. 만든 쪽지는 회차 완료를 요청하기 전까지 앱에 보이지 않고, 완료를 이미 요청했으면 바로 도착한다.
     * 회차당 한 통이라 여러 번 불러도 다시 만들지 않는다(다른 실행이 만드는 중이면 그대로 둔다). 어떤 오류도 던지지 않는다.
     *
     * @return 쪽지가 있거나 만드는 중이면 true. 만들 수 없었으면(미리 만들기를 쓸 수 없는 AI 연결, 생성 실패) false —
     *         회차 완료가 정리 단계 뒤 기존 방식으로 만든다
     */
    boolean prepareEpisodeNote(Long memberId, Long seasonPk, int order) {
        if (order >= AiStoryCatalog.FINAL_ORDER) {
            return false;
        }
        AiStoryStateWriter.NoteReservation reservation = null;
        try {
            AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
            AiStorySnapshot.Content content = snapshot.content(order)
                    .filter(c -> c.status() == AiStoryContentStatus.READY).orElse(null);
            Integer pointId = AiStoryCatalog.pointOf(order);
            AiStorySnapshot.Choice choice = pointId == null ? null : snapshot.choice(pointId).orElse(null);
            if (content == null || pointId != null && (choice == null || !choice.resultReady())) {
                return false;
            }
            AiStoryCastMember sender = noteSender(snapshot, content, order);
            try {
                reservation = stateWriter.reserveEpisodeNote(seasonPk, order, sender.breadId(), staleBefore())
                        .orElse(null);
            } catch (DataIntegrityViolationException raceLoser) {
                return true; // 다른 실행이 같은 쪽지를 먼저 만들기 시작했다.
            }
            if (reservation == null) {
                return true;
            }
            String ownerSubject = ownerSubjectProvider.resolve(memberId);
            AiStoryContractMapper mapper = new AiStoryContractMapper(snapshot, storedStageIntro(snapshot));
            StoryTypes.GenerationContext context = context(snapshot, mapper, userWithAge(memberId).getAge(), memberId);
            List<AiSceneBlockView> resultBlocks = choice == null ? List.of()
                    : AiStoryJson.read(choice.resultBlocksJson(), BLOCK_LIST);
            AiStorySnapshot.Relation senderRelation = snapshot.relation(sender.breadId()).orElse(null);
            PreviewNoteRequest request = new PreviewNoteRequest(context, order,
                    AiStoryCatalog.settingScene(snapshot.setting(), order),
                    mapper.sceneBlocks(AiStoryJson.read(content.blocksJson(), BLOCK_LIST), resultBlocks),
                    choice == null ? null : mapper.choiceRecords().stream()
                            .filter(c -> c.pointId() == pointId).findFirst().orElse(null),
                    mapper.relationRecords(),
                    new EpisodeNoteRequest.Sender(sender.castRole().contractId(),
                            senderRelation == null ? null : senderRelation.speechLevel().wireValue(), sender.name(),
                            sender.castRole().attachmentStyle().wireValue(),
                            senderRelation == null ? null : senderRelation.statusText()),
                    stateWriter.findEpisodeNoteBodies(seasonPk).stream().map(AiStoryService::firstSentence).toList());
            List<String> castIds = mapper.castIds();
            StoryRun run = new StoryRun(jobKeyBase(snapshot, content.id(), "note", reservation.attempt()),
                    StepRecorder.NONE);
            EpisodeNoteResponse note = callWithRetry("episode-note-preview", run,
                    call -> storyClient.previewEpisodeNote(ownerSubject, request, call),
                    r -> AiStoryResponseValidator.validateNote(r, castIds));
            stateWriter.saveEpisodeNote(reservation.letterPk(), note.body(),
                    note.renderMode() != null ? note.renderMode() : NOTE_RENDER_MODE);
            return true;
        } catch (AiStoryFeatureUnsupportedException unsupported) {
            if (reservation != null) {
                stateWriter.releaseEpisodeNote(reservation.letterPk());
            }
            return false;
        } catch (Exception e) {
            Throwable cause = e instanceof AiStoryGenerationFailedException && e.getCause() != null ? e.getCause() : e;
            log.warn("story episode note preview failed: seasonPk={}, order={}, cause={}", seasonPk, order,
                    cause.getClass().getSimpleName());
            if (reservation != null) {
                stateWriter.markEpisodeNoteFailed(reservation.letterPk(), AiStoryGenerationFailedException.CODE);
            }
            return false;
        }
    }

    /**
     * 회차 완료 중: 쪽지가 있는지 확인하고, 없거나 실패했으면 만든다. 미리 만들던 쪽지가 아직 진행 중이면 끝날 때까지
     * (jobWaitTimeout까지) 기다렸다가, 실패했으면 다시 만든다. 그래도 끝나지 않으면 그 실행이 끝나는 대로 도착하도록 둔다.
     *
     * @return 쪽지가 있거나 아직 만드는 중이면 true. 만들 수 없었으면 false(정리 단계 뒤 기존 방식으로 만든다)
     */
    private boolean ensureEpisodeNote(Long memberId, Long seasonPk, int order) {
        long deadline = System.nanoTime() + properties.jobWaitTimeout().toNanos();
        while (prepareEpisodeNote(memberId, seasonPk, order)) {
            AiJobStatus status = stateWriter.episodeNoteStatus(seasonPk, order).orElse(null);
            if (status == AiJobStatus.SUCCEEDED || System.nanoTime() > deadline) {
                return true;
            }
            try {
                Thread.sleep(properties.jobPollInterval().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
        return false;
    }

    /**
     * 안 읽은 쪽지가 없을 때(GET /letters/unread) 부른다: 완료한 최근 회차(1~9화)의 쪽지가 실패해 없으면 백그라운드에서
     * 다시 만든다. 만들어지면 다음 조회부터 보인다.
     */
    public void retryMissingEpisodeNote(Long memberId) {
        seasonRepository.findFirstByMemberIdOrderBySeasonNumberDesc(memberId).ifPresent(season -> {
            AiStorySnapshot snapshot = stateWriter.snapshot(season.getId());
            snapshot.contents().stream()
                    .filter(c -> c.completed() && c.order() < AiStoryCatalog.FINAL_ORDER)
                    .max(java.util.Comparator.comparingInt(AiStorySnapshot.Content::order))
                    .filter(c -> stateWriter.episodeNoteStatus(season.getId(), c.order())
                            .map(status -> status == AiJobStatus.FAILED).orElse(true))
                    .ifPresent(c -> executor.execute(() -> prepareEpisodeNote(memberId, season.getId(), c.order())));
        });
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
        // 연 회차는 앱이 곧 들어오므로 미리 만든다(이미 있거나 만드는 중이면 아무것도 하지 않는다).
        Long seasonPk = season.getId();
        executor.execute(() -> prefetch(memberId, seasonPk, order));
        return new AiEpisodeUnlockView(order, true, stateWriter.tokenBalance(memberId));
    }

    /**
     * 백그라운드 생성. 어떤 오류든 FAILED로 남겨 다음 GET content가 다시 시도하게 한다(스레드 밖으로 던지지 않는다).
     * 결과와 실패는 같은 생성 회차(round)일 때만 저장한다. 단계마다 AI job 키를 회차 row·회차 번호·단계·시도로 만들어,
     * 재시작 뒤 같은 회차를 다시 돌리면 이미 접수한 job을 이어서 본다.
     */
    void generate(Long memberId, Long seasonPk, int order, Long contentPk, int round) {
        generate(memberId, seasonPk, order, contentPk, round, false);
    }

    /** prefetched: 앱이 요청하기 전에 시작한 다음 회차 미리 생성({@link #prefetch}). 실패를 앱에 알리지 않는다. */
    void generate(Long memberId, Long seasonPk, int order, Long contentPk, int round, boolean prefetched) {
        try {
            AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
            StoryRun run = new StoryRun(jobKeyBase(snapshot, contentPk, "gen", round),
                    (stage, jobId, regenerating) ->
                            stateWriter.recordGenerationProgress(contentPk, round, stage, jobId, regenerating));
            String ownerSubject = ownerSubjectProvider.resolve(memberId);
            User user = userWithAge(memberId);
            int age = user.getAge();
            AiSeasonDesignRecord seasonDesign = stateWriter.findSeasonDesign(seasonPk).orElse(null);
            AiStoryStateWriter.StreamState stream = stateWriter.streamState(contentPk);
            AiStageIntroView stageIntro;
            if (order != AiStoryCatalog.FIRST_ORDER) {
                stageIntro = storedStageIntro(snapshot);
            } else if (stream.stageIntroJson() != null) {
                // 이어 쓰기: 이미 앱에 보여 준 무대 소개를 그대로 쓴다.
                stageIntro = AiStoryJson.read(stream.stageIntroJson(), AiStageIntroView.class);
            } else {
                if (seasonDesign == null && snapshot.seasonNumber() > 1) {
                    seasonDesign = designSeason(ownerSubject, snapshot, user, run).orElse(null);
                    // 설정을 반영하면 NPC 이름·빵 종류가 바뀐다 — 본문 입력은 바뀐 출연진으로 만든다.
                    snapshot = stateWriter.snapshot(seasonPk);
                }
                stageIntro = seasonDesign != null ? AiSeasonDesigns.stageIntro(snapshot, seasonDesign)
                        : requestStageIntro(ownerSubject, snapshot, age, run);
            }
            AiStoryContractMapper mapper = new AiStoryContractMapper(snapshot, stageIntro);
            StoryTypes.GenerationContext context = context(snapshot, mapper, age, memberId);

            AiEpisodePlan plan = AiEpisodePlanner.plan(planInput(snapshot, order));
            List<BlockPlan> designed = plan.episodeBlocks();
            // 10화 인터뷰룸(5-4)은 본문과 입력이 독립이라 본문과 동시에 만든다. 진행 단계(progress.stage)는 본문 기준으로만 남긴다.
            AiStorySnapshot interviewSnapshot = snapshot;
            CompletableFuture<Map<String, List<StoryTypes.Message>>> interviews = plan.interviewBlocks().isEmpty()
                    ? CompletableFuture.completedFuture(Map.of())
                    : CompletableFuture.supplyAsync(() -> requestInterviews(ownerSubject, interviewSnapshot, mapper,
                            context, new StoryRun(run.keyBase(), StepRecorder.NONE)), executor);
            EpisodeRequest request = new EpisodeRequest(context, order, plan.commonEvent(), plan.settingScene(),
                    plan.route() == null ? null : plan.route().wireValue(),
                    mapper.contractIdOrNull(snapshot.romancePartnerId()),
                    seasonDesign == null ? plan.referencePlot()
                            : AiSeasonDesigns.referencePlot(plan.referencePlot(), seasonDesign, order),
                    designed.stream().map(mapper::design).toList(), plan.openThreads(), mapper.relationRecords(),
                    mapper.choiceRecords(), usedMaterials(snapshot, order),
                    seasonDesign == null ? null
                            : AiSeasonDesigns.episodeContext(snapshot.seasonNumber(), seasonDesign, order));
            String stageIntroJson = order == AiStoryCatalog.FIRST_ORDER ? AiStoryJson.write(stageIntro) : null;
            BlockAssembly assembly = new BlockAssembly(plan, snapshot, mapper);
            EpisodeResponse episode = writeEpisodeBody(ownerSubject, contentPk, round, request, stream, assembly,
                    stageIntroJson, run,
                    r -> AiStoryResponseValidator.validateEpisode(r, designed.stream().map(mapper::expected).toList()));

            // 생성 중 앱에 나간 블록(메시지 id 포함)은 그대로 두고 남은 블록과 인터뷰만 붙인다.
            AiStoryStateWriter.StreamState saved = stateWriter.streamState(contentPk);
            List<AiSceneBlockView> blocks = assembly.append(readBlocks(saved.blocksJson()), episode.blocks(),
                    interviews.join());
            if (blocks.size() != plan.blocks().size()) {
                throw new AiStoryInvalidResponseException("본문에 없는 블록이 있습니다.");
            }
            String introJson = saved.introJson() != null ? saved.introJson() : AiStoryJson.write(introView(episode.intro()));
            stateWriter.saveGenerated(contentPk, round, introJson, stageIntroJson, AiStoryJson.write(blocks));
            if (AiStoryCatalog.pointOf(order) == null) {
                // 선택이 없는 회차는 본문이 준비되면 쪽지를 미리 만든다(선택 회차는 선택 결과 장면 뒤).
                prepareEpisodeNote(memberId, seasonPk, order);
            }
        } catch (Exception e) {
            Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
            log.warn("story episode generation failed: seasonPk={}, order={}, prefetched={}, cause={}",
                    seasonPk, order, prefetched, cause.getClass().getSimpleName());
            stateWriter.markGenerationFailed(contentPk, round, AiStoryGenerationFailedException.CODE, prefetched);
        }
    }

    /**
     * 본문(5-2)을 블록 단위로 확정하며 만든다(AI episode-stream). 확정된 블록은 바로 앱용 블록으로 바꿔 저장해, 생성 중인
     * GET content에도 나간다. 한 번 나간 블록은 바꾸지 않는다:
     * - 실패하면 같은 입력으로 실패한 job에서 이어 쓴다(이 실행 안에서 한 번 더, 다음 GET content의 다시 시도에서도).
     * - AI가 이어 쓰기를 거부하면(입력·설정 변경) 나간 블록을 버리고 처음부터 만든다 — revision이 올라 앱이 처음부터 재생한다.
     * - 끝난 결과가 Spring 검사에 걸리면 이어 쓸 수 없으므로 나간 블록을 버리고 실패로 남긴다(다음 시도는 처음부터).
     * 블록 단위 생성을 쓸 수 없는 AI 연결이면 기존 본문 생성(writeEpisode)으로 만든다.
     */
    private EpisodeResponse writeEpisodeBody(String ownerSubject, Long contentPk, int round, EpisodeRequest request,
                                             AiStoryStateWriter.StreamState stream, BlockAssembly assembly,
                                             String stageIntroJson, StoryRun run, Consumer<EpisodeResponse> validate) {
        JsonNode input;
        UUID previousJobId = null;
        if (stream.jobId() != null && stream.inputJson() != null) {
            input = AiStoryJson.read(stream.inputJson(), JsonNode.class);
            previousJobId = stream.jobId();
        } else {
            if (stream.blocksJson() != null) {
                stateWriter.resetStream(contentPk, round); // 이어 쓸 job 없이 남은 블록은 쓰지 않는다.
            }
            input = AiStoryConfig.STORY_OBJECT_MAPPER.valueToTree(request);
            stateWriter.startStream(contentPk, round, AiStoryJson.write(input), assembly.plan().blocks().size(),
                    stageIntroJson);
        }
        run.recorder().record("episode", null, false);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            UUID[] currentJob = {previousJobId};
            GenerationCall call = new GenerationCall(run.key("episode", attempt), progress -> {
                run.recorder().record("episode", progress.jobId(), progress.regenerating());
                if (progress.jobId() != null && !progress.jobId().equals(currentJob[0])) {
                    currentJob[0] = progress.jobId();
                    stateWriter.recordStreamJob(contentPk, round, progress.jobId());
                }
            });
            try {
                EpisodeResponse episode = storyClient.streamEpisode(ownerSubject, input, previousJobId, call,
                        partial -> appendPartial(contentPk, round, assembly, partial));
                try {
                    validate.accept(episode);
                } catch (AiStoryInvalidResponseException invalid) {
                    stateWriter.resetStream(contentPk, round);
                    throw new AiStoryGenerationFailedException("episode", invalid);
                }
                return episode;
            } catch (AiStoryFeatureUnsupportedException unsupported) {
                if (stateWriter.streamState(contentPk).blocksJson() != null) {
                    stateWriter.resetStream(contentPk, round);
                }
                return callWithRetry("episode", run, c -> storyClient.writeEpisode(ownerSubject, request, c), validate);
            } catch (AiStreamResumeRejectedException rejected) {
                log.info("story episode stream resume rejected, restarting: contentPk={}, code={}", contentPk,
                        rejected.getAiErrorCode());
                stateWriter.resetStream(contentPk, round);
                input = AiStoryConfig.STORY_OBJECT_MAPPER.valueToTree(request);
                stateWriter.startStream(contentPk, round, AiStoryJson.write(input), assembly.plan().blocks().size(),
                        stageIntroJson);
                previousJobId = null;
                last = rejected;
            } catch (AiServiceException failed) {
                log.warn("story episode stream attempt {} failed: {}", attempt, failed.getMessage());
                previousJobId = currentJob[0];
                last = failed;
            }
        }
        throw new AiStoryGenerationFailedException("episode", last);
    }

    /** 새로 확정된 블록을 앱용 블록으로 바꿔 이어 붙인다. 다른 실행이 먼저 저장했으면 그쪽 블록을 그대로 둔다. */
    private void appendPartial(Long contentPk, int round, BlockAssembly assembly, EpisodePartial partial) {
        AiStoryStateWriter.StreamState state = stateWriter.streamState(contentPk);
        List<AiSceneBlockView> built = readBlocks(state.blocksJson());
        List<AiSceneBlockView> views = assembly.append(built, partial.blocks(), Map.of());
        boolean newIntro = state.introJson() == null && partial.intro() != null;
        if (views.size() == built.size() && !newIntro) {
            return;
        }
        stateWriter.appendPartial(contentPk, round, built.size(),
                partial.intro() == null ? null : AiStoryJson.write(introView(partial.intro())), AiStoryJson.write(views));
    }

    /**
     * 2시즌부터 1화 무대 소개 대신 새 시즌 설정을 만든다(FastAPI season-design). 설정이 끝나 저장(출연진 이름·빵 종류 반영)된
     * 뒤에 본문을 부른다. 같은 생성 회차를 다시 돌리면 같은 job 키라 같은 설정 job을 이어서 보고, 저장은 시즌당 한 번만
     * 한다. 직전 시즌에 비교할 기록(흐름·리포트)이 없으면 empty — 호출부는 기존 무대 소개를 쓴다.
     */
    private Optional<AiSeasonDesignRecord> designSeason(String ownerSubject, AiStorySnapshot snapshot, User user,
                                                        StoryRun run) {
        List<AiPastSeason> past = stateWriter.pastSeasons(snapshot.memberId(), snapshot.seasonNumber());
        String myBreadType = snapshot.me().breadType();
        List<String> allowedBreads = breadCatalog.allBreadLabels().stream()
                .filter(bread -> !bread.equals(myBreadType))
                .toList();
        Optional<SeasonDesignRequest> request = AiSeasonDesigns.request(snapshot.seasonNumber(), user.getGender(),
                user.getAge(), stateWriter.seasonTraits(snapshot.memberId(), snapshot.seasonNumber()).orElse(null),
                snapshot.setting(), past, allowedBreads);
        if (request.isEmpty()) {
            log.info("season design skipped, no comparable previous season: seasonPk={}, seasonNumber={}",
                    snapshot.seasonPk(), snapshot.seasonNumber());
            return Optional.empty();
        }
        SeasonDesignResponse response = callWithRetry("season-design", run,
                call -> storyClient.designSeason(ownerSubject, request.get(), call),
                r -> AiSeasonDesigns.validate(r, request.get()));
        return Optional.of(stateWriter.applySeasonDesign(snapshot.seasonPk(),
                AiSeasonDesigns.record(request.get(), response, past)));
    }

    private AiStageIntroView requestStageIntro(String ownerSubject, AiStorySnapshot snapshot, int age, StoryRun run) {
        List<StageIntroRequest.Cast> cast = snapshot.cast().stream()
                .map(m -> new StageIntroRequest.Cast(m.castRole().contractId(), m.name(), m.breadType(), m.gender(),
                        m.castRole().fastApiCastRole(), m.castRole().attachmentStyle().wireValue(),
                        m.castRole().seasonRole()))
                .toList();
        StageIntroRequest request = new StageIntroRequest(snapshot.setting().wireValue(),
                AiSeasonSettingPolicy.audience(age), AiSeasonSettingPolicy.ageGroup(age), cast);
        List<String> castIds = cast.stream().map(StageIntroRequest.Cast::breadId).toList();
        StageIntroResponse response = callWithRetry("stage-intro", run,
                call -> storyClient.stageIntro(ownerSubject, request, call),
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
                                                                    StoryTypes.GenerationContext context,
                                                                    StoryRun run) {
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
        InterviewResponse response = callWithRetry("interview", run,
                call -> storyClient.writeInterview(ownerSubject, request, call),
                r -> AiStoryResponseValidator.validateInterview(r, interviewees));
        Map<String, List<StoryTypes.Message>> byBread = new HashMap<>();
        response.interviews().forEach(i -> byBread.put(i.breadId(), i.messages()));
        return byBread;
    }

    // ---------------------------------------------------------------- 선택

    /**
     * 3·5·6·8화 끝 선택. 판정은 코드 규칙으로 하고, 결과 장면만 FastAPI가 쓴다(5-3). 혼자 있기는 FastAPI를 부르지 않는다.
     *
     * 접수 후 폴링: 선택(수락·거절 판정, 호감·흐름 반영)은 바로 저장하고, 결과 장면은 백그라운드에서 만든다 — 응답의
     * choice.status가 generating이면 시즌 상태(story.choice)를 폴링한다. 같은 선택을 다시 보내면 만드는 중이면 상태만,
     * 끝났으면 저장된 결과를, 실패했으면(또는 오래 멈췄으면) 결과 장면만 다시 만든다. 선택 자체는 바뀌지 않으므로 다른
     * 빵으로 다시 고르려 하면 409 CHOICE_ALREADY_MADE.
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
            return existingOutcome(memberId, season.getId(), existing.get(), breadId);
        }
        if (snapshot.status() == AiSeasonStatus.COMPLETED) {
            throw new AiSeasonCompletedException();
        }
        AiStorySnapshot.Content content = readyContent(snapshot, order);
        if (breadId != null && !choiceCandidates(content).contains(breadId)) {
            throw new AiCandidateInvalidException("breadId가 이 선택 카드의 후보가 아닙니다.");
        }
        if (breadId != null) {
            userWithAge(memberId); // AGE_REQUIRED는 백그라운드로 넘기기 전에 409로 알린다.
        }

        AiChoiceJudge.Decision decision = AiChoiceJudge.judge(judgeInput(snapshot, pointId, breadId));
        // 혼자 있기는 결과 장면에 대사가 없어 바로 저장한다. 그 외는 결과 장면 없이 저장하고 백그라운드에서 만든다.
        String aloneBlocks = breadId == null ? AiStoryJson.write(aloneResultBlocks(snapshot, pointId, decision)) : null;
        try {
            stateWriter.saveChoice(season.getId(), pointId, order, breadId, decision, aloneBlocks);
        } catch (DataIntegrityViolationException raceLoser) {
            AiStorySnapshot reloaded = stateWriter.snapshot(season.getId());
            return existingOutcome(memberId, season.getId(),
                    reloaded.choice(pointId).orElseThrow(() -> raceLoser), breadId);
        }
        Long seasonPk = season.getId();
        if (breadId != null) {
            startChoiceResult(memberId, seasonPk, pointId, order, 1);
        } else {
            executor.execute(() -> prepareEpisodeNote(memberId, seasonPk, order)); // 혼자 있기는 결과가 바로 준비된다.
        }
        return choiceOutcome(stateWriter.snapshot(seasonPk), pointId);
    }

    /** 이미 한 선택을 다시 보냈다. 다른 빵이면 409, 결과 장면이 실패했거나 멈췄으면 다시 만들기 시작한다. */
    private AiChoiceOutcomeView existingOutcome(Long memberId, Long seasonPk, AiStorySnapshot.Choice choice,
                                                UUID requestedBreadId) {
        if (!Objects.equals(choice.breadId(), requestedBreadId)) {
            throw new AiStoryConflictException(AiStoryConflictException.CHOICE_ALREADY_MADE, "이미 선택을 마쳤습니다.");
        }
        if (!choice.resultReady()) {
            stateWriter.restartChoiceResult(seasonPk, choice.pointId(), staleBefore())
                    .ifPresent(round -> startChoiceResult(memberId, seasonPk, choice.pointId(), choice.episodeOrder(),
                            round));
        }
        return choiceOutcome(stateWriter.snapshot(seasonPk), choice.pointId());
    }

    private void startChoiceResult(Long memberId, Long seasonPk, int pointId, int order, int round) {
        executor.execute(() -> runChoiceResult(memberId, seasonPk, pointId, order, round));
    }

    /**
     * 정리 단계 결과로 쓰는 기존 회차 쪽지(5-6 episode-note). 10화 시즌 종료 쪽지와, 1~9화 쪽지를 미리 만들지 못했을 때
     * (미리 만들기를 쓸 수 없는 AI 연결이거나 실패)만 쓴다.
     */
    private AiStoryStateWriter.EpisodeNote writeNoteAfterSummary(String ownerSubject, AiStorySnapshot snapshot,
                                                                 AiStorySnapshot.Content content, int order,
                                                                 AiStoryContractMapper mapper,
                                                                 StoryTypes.GenerationContext context,
                                                                 SummarizeResponse summary, StoryRun run) {
        Integer pointId = AiStoryCatalog.pointOf(order);
        List<String> castIds = mapper.castIds();
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
                new EpisodeNoteRequest.Sender(sender.castRole().contractId(),
                        snapshot.relation(sender.breadId()).map(r -> r.speechLevel().wireValue()).orElse(null),
                        sender.name(),
                        sender.castRole().attachmentStyle().wireValue(), senderStatus),
                stateWriter.findEpisodeNoteBodies(snapshot.seasonPk()).stream().map(AiStoryService::firstSentence)
                        .toList());
        EpisodeNoteResponse note = callWithRetry("episode-note", run,
                call -> storyClient.writeEpisodeNote(ownerSubject, noteRequest, call),
                r -> AiStoryResponseValidator.validateNote(r, castIds));

        return new AiStoryStateWriter.EpisodeNote(sender.breadId(), note.body(), note.renderMode());
    }

    /** 응답: 결과 장면이 준비됐으면 그 블록, 아니면 빈 배열과 생성 상태. */
    private AiChoiceOutcomeView choiceOutcome(AiStorySnapshot snapshot, int pointId) {
        AiStorySnapshot.Choice choice = snapshot.choice(pointId).orElseThrow();
        List<AiSceneBlockView> blocks = choice.resultReady()
                ? AiStoryJson.read(choice.resultBlocksJson(), BLOCK_LIST) : List.of();
        return new AiChoiceOutcomeView(blocks, toStateView(snapshot), choiceStatus(choice));
    }

    private AiChoiceStatusView choiceStatus(AiStorySnapshot.Choice choice) {
        String status;
        String errorCode = null;
        if (choice.resultReady()) {
            status = AiChoiceStatusView.READY;
        } else if (choice.resultStatus() == AiStoryContentStatus.GENERATING
                && choice.resultRequestedAt() != null && !choice.resultRequestedAt().isBefore(staleBefore())) {
            status = AiChoiceStatusView.GENERATING;
        } else {
            // 실패했거나, 생성 중인 채 오래 멈췄다(서버 재시작 등) — 같은 선택을 다시 보내면 다시 만든다.
            status = AiChoiceStatusView.FAILED;
            errorCode = choice.resultErrorCode() != null ? choice.resultErrorCode()
                    : AiStoryGenerationFailedException.CODE;
        }
        return new AiChoiceStatusView(choice.episodeOrder(), choice.pointId(), choice.breadId(), status, errorCode);
    }

    /** 혼자 있기 결과: 내 빵만 있는 choice_result 블록 하나(대사 없음). */
    private List<AiSceneBlockView> aloneResultBlocks(AiStorySnapshot snapshot, int pointId,
                                                     AiChoiceJudge.Decision decision) {
        AiStoryContractMapper mapper = new AiStoryContractMapper(snapshot, storedStageIntro(snapshot));
        BlockPlan reaction = AiEpisodePlanner.planChoiceReaction(pointId, null, decision, snapshot.cast());
        return List.of(new AiSceneBlockView(reaction.id(), AiSceneBlockType.CHOICE_RESULT.wireValue(),
                mapper.participants(reaction.participantIds()), List.of(), null, null, null,
                new AiSceneBlockView.SceneChoiceResult(pointId, null, decision.accepted()), null));
    }

    /**
     * 백그라운드 선택 결과 장면 생성. 어떤 오류든 FAILED로 남겨 같은 선택을 다시 보내면 다시 만들게 한다(스레드 밖으로 던지지
     * 않는다). 결과와 실패는 같은 생성 회차(round)일 때만 저장한다. AI job 키는 선택·회차 번호·시도로 만들어, 재시작 뒤 같은
     * 회차를 다시 돌리면 이미 접수한 job을 이어서 본다.
     */
    void runChoiceResult(Long memberId, Long seasonPk, int pointId, int order, int round) {
        try {
            stateWriter.saveChoiceResult(seasonPk, pointId, round,
                    AiStoryJson.write(writeChoiceResult(memberId, seasonPk, pointId, order, round)));
            prepareEpisodeNote(memberId, seasonPk, order); // 선택 결과 장면까지 준비됐으니 쪽지를 미리 만든다.
        } catch (Exception e) {
            log.warn("story choice result failed: seasonPk={}, pointId={}, cause={}",
                    seasonPk, pointId, e.getClass().getSimpleName());
            stateWriter.markChoiceResultFailed(seasonPk, pointId, round, AiStoryGenerationFailedException.CODE);
        }
    }

    /**
     * 저장된 판정으로 결과 장면을 만든다. 관계 기록은 선택을 반영한 뒤의 값이다(수락이면 호감이 오른 뒤, 거절이면 거절 횟수가
     * 늘어난 뒤).
     */
    private List<AiSceneBlockView> writeChoiceResult(Long memberId, Long seasonPk, int pointId, int order, int round) {
        AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
        AiStorySnapshot.Choice choice = snapshot.choice(pointId).orElseThrow();
        AiStorySnapshot.Content content = readyContent(snapshot, order);
        UUID breadId = choice.breadId();
        AiChoiceJudge.Decision decision = new AiChoiceJudge.Decision(choice.accepted(), choice.reasonCode(),
                AiChoiceRules.REASON_AVOIDANT_SHORT.equals(choice.reasonCode()));
        AiStoryContractMapper mapper = new AiStoryContractMapper(snapshot, storedStageIntro(snapshot));
        BlockPlan reaction = AiEpisodePlanner.planChoiceReaction(pointId, breadId, decision, snapshot.cast());
        AiSceneBlockView.SceneChoiceResult result = new AiSceneBlockView.SceneChoiceResult(pointId, breadId,
                decision.accepted());

        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        int age = userWithAge(memberId).getAge();
        String friendId = AiCastRole.FRIEND.contractId();
        ChoiceResultRequest request = new ChoiceResultRequest(context(snapshot, mapper, age, memberId), order,
                pointId, mapper.contractId(breadId), decision.accepted(),
                AiChoiceRules.rejectReason(decision.accepted(), decision.reasonCode()), decision.shortReply(),
                mapper.relationRecord(breadId));
        Set<String> speakers = mapper.contractIds(reaction.participantIds());
        StoryRun run = new StoryRun(jobKeyBase(snapshot, content.id(), "choice-p" + pointId + "-", round),
                StepRecorder.NONE);
        ChoiceResultResponse response = callWithRetry("choice-result", run,
                call -> storyClient.writeChoiceResult(ownerSubject, request, call),
                r -> AiStoryResponseValidator.validateChoiceResult(r, speakers, decision.accepted(), friendId));

        List<AiSceneBlockView> blocks = new ArrayList<>();
        int[] sequence = {1};
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
        return blocks;
    }

    // ---------------------------------------------------------------- 회차 완료

    /**
     * 회차 완료 접수. 완료(정리·쪽지·리포트 생성)는 FastAPI 호출을 여러 번 기다려 수십 초가 걸리므로 백그라운드에서 하고,
     * 바로 상태를 돌려준다 — 진행 중이면 completion.status가 completing, 실패하면 failed다. 앱은 시즌 상태를 폴링하다가
     * failed면 이 요청을 다시 보내 재시도한다. 이미 완료한 회차면 상태만 돌려주고, 완료 중에 다시 보내도 새로 시작하지 않는다.
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
        if (pointId != null && !snapshot.choice(pointId).orElseThrow().resultReady()) {
            throw new AiStoryConflictException(AiStoryConflictException.CHOICE_RESULT_NOT_READY,
                    "선택 결과 장면이 아직 준비되지 않았습니다.");
        }
        userWithAge(memberId); // AGE_REQUIRED는 백그라운드로 넘기기 전에 409로 알린다.

        AiStoryStateWriter.GenerationTicket ticket;
        try {
            ticket = stateWriter.beginCompletion(season.getId(), order, completionStaleBefore());
        } catch (ObjectOptimisticLockingFailureException concurrentStart) {
            ticket = null; // 다른 요청이 먼저 시작했다 — 그쪽 완료 상태를 그대로 보여준다.
        }
        if (ticket != null && ticket.started()) {
            Long seasonPk = season.getId();
            Long contentPk = ticket.contentPk();
            int round = ticket.round();
            executor.execute(() -> runCompletion(memberId, seasonPk, order, contentPk, round));
        }
        return toStateView(stateWriter.snapshot(season.getId()));
    }

    /**
     * 백그라운드 회차 완료: (1~9화) 쪽지 확인 → 정리 단계(5-5) → (10화면) 시즌 종료 쪽지(5-6)·최종 리포트(5-7)를 FastAPI로
     * 만들고 한 트랜잭션에 저장한다. 1~9화 쪽지는 미리 만들어 완료 요청에서 도착하므로 정리 단계를 기다리지 않고, 쪽지
     * 실패는 완료를 실패시키지 않는다. 그 밖의 단계가 실패하면 아무것도 저장하지 않고 완료 실패로 남긴다(스레드 밖으로
     * 던지지 않는다).
     */
    void runCompletion(Long memberId, Long seasonPk, int order, Long contentPk, int round) {
        try {
            completeEpisode(memberId, seasonPk, order, contentPk, round);
        } catch (Exception e) {
            log.warn("story episode completion failed: seasonPk={}, order={}, cause={}",
                    seasonPk, order, e.getClass().getSimpleName());
            stateWriter.markCompletionFailed(contentPk, round, AiStoryGenerationFailedException.CODE);
        }
    }

    private void completeEpisode(Long memberId, Long seasonPk, int order, Long contentPk, int round) {
        AiStorySnapshot snapshot = stateWriter.snapshot(seasonPk);
        StoryRun run = new StoryRun(jobKeyBase(snapshot, contentPk, "done", round),
                (stage, jobId, regenerating) ->
                        stateWriter.recordCompletionProgress(contentPk, round, stage, jobId, regenerating));
        AiStorySnapshot.Content content = readyContent(snapshot, order);
        Integer pointId = AiStoryCatalog.pointOf(order);
        // 1~9화 쪽지는 정리 단계를 기다리지 않는다: 미리 만든 쪽지는 완료 요청에서 이미 도착했고, 없거나 실패했으면 여기서
        // 먼저 만들어 바로 도착시킨다. 쪽지가 실패해도 회차 완료(관계 갱신)는 계속한다.
        boolean notePrepared = order < AiStoryCatalog.FINAL_ORDER && ensureEpisodeNote(memberId, seasonPk, order);
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
        SummarizeResponse summary = callWithRetry("summarize", run,
                call -> storyClient.summarize(ownerSubject, summarizeRequest, call),
                r -> AiStoryResponseValidator.validateSummary(r, relationIds));

        AiStoryStateWriter.EpisodeNote episodeNote = null;
        if (!notePrepared) {
            try {
                episodeNote = writeNoteAfterSummary(ownerSubject, snapshot, content, order, mapper, context, summary,
                        run);
            } catch (RuntimeException noteFailure) {
                if (order == AiStoryCatalog.FINAL_ORDER) {
                    throw noteFailure; // 시즌 종료 쪽지는 리포트와 함께 완료 조건이다.
                }
                log.warn("story episode note failed, completing without it: seasonPk={}, order={}, cause={}",
                        seasonPk, order, noteFailure.getClass().getSimpleName());
            }
        }

        String reportJson = null;
        if (order == AiStoryCatalog.FINAL_ORDER) {
            List<StoryTypes.EpisodeSummary> summaries = episodeSummaries(snapshot, order, summary.episodeSummary());
            StoryTypes.ReportSeasonContext seasonContext = stateWriter.findSeasonDesign(seasonPk)
                    .map(design -> AiSeasonDesigns.reportContext(snapshot.seasonNumber(), design))
                    .orElse(null);
            SeasonReportRequest reportRequest = new SeasonReportRequest(context,
                    snapshot.route() == null ? null : snapshot.route().wireValue(),
                    mapper.contractIdOrNull(snapshot.romancePartnerId()), summaries, mapper.choiceRecordsWithReason(),
                    mapper.relationRecords(), seasonContext);
            SeasonReportResponse report = callWithRetry("season-report", run,
                    call -> storyClient.writeSeasonReport(ownerSubject, reportRequest, call),
                    r -> AiStoryResponseValidator.validateReport(r, castIds));
            reportJson = AiStoryJson.write(toReportView(report, snapshot));
        }

        List<AiStoryStateWriter.RelationChange> changes = summary.relations().stream()
                .map(u -> new AiStoryStateWriter.RelationChange(mapper.uuid(u.breadId()), u.memorySummary(),
                        u.recentMemory(), u.newFacts(), u.threadsAdd(), u.threadsResolved(), u.statusText(),
                        u.affinityDelta(), u.choiceMemory(), u.speechLevel()))
                .toList();
        // 생성 결과는 그대로 두고 저장만 다시 한다. 그사이 같은 시즌을 다른 요청(피드백 등)이 바꾸면 낙관적 락 충돌이 난다.
        for (int attempt = 1; ; attempt++) {
            try {
                stateWriter.completeEpisode(seasonPk, order, round, summary.episodeSummary(), changes, episodeNote,
                        reportJson);
                // 무료 회차(2·3화)는 완료 직후 다음 회차를 미리 만든다. 4화부터는 열 때(unlock) 시작한다.
                if (AiStoryCatalog.isFree(order + 1)) {
                    prefetch(memberId, seasonPk, order + 1);
                }
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= SAVE_ATTEMPTS) {
                    throw e;
                }
                log.info("story completion save conflict, retrying: seasonPk={}, order={}", seasonPk, order);
            }
        }
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
        // 생성 중이어도 이미 나간 블록의 대사에는 남길 수 있다(블록 단위 생성).
        AiStorySnapshot.Content content = snapshot.content(order)
                .filter(c -> c.status() == AiStoryContentStatus.READY || c.blocksJson() != null)
                .orElseThrow(() -> new AiStoryConflictException(AiStoryConflictException.CONTENT_NOT_READY,
                        order + "화가 아직 준비되지 않았습니다."));
        List<AiSceneBlockView> blocks = new ArrayList<>(readBlocks(content.blocksJson()));
        Integer pointId = AiStoryCatalog.pointOf(order);
        if (pointId != null) {
            snapshot.choice(pointId).ifPresent(c -> blocks.addAll(readBlocks(c.resultBlocksJson())));
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

    // ---------------------------------------------------------------- 재시작 복구

    /**
     * 끝나지 않은 회차 생성·완료를 이어서 진행한다(기동 때 한 번). 멈춘 것으로 보기 전(stale 전)에 시작한 것만 고른다.
     * 같은 회차 번호로 다시 돌리므로 같은 AI job 키로 접수해, 이미 접수했거나 끝난 job을 이어서 본다(유료 생성을 새로
     * 하지 않는다). 결과 저장은 회차 번호와 상태를 다시 확인하므로, 다른 서버가 같은 작업을 끝냈어도 한 번만 반영된다.
     */
    public void resumeInterrupted() {
        List<AiStoryStateWriter.PendingWork> generations = stateWriter.findInterruptedGenerations(staleBefore());
        List<AiStoryStateWriter.PendingWork> completions =
                stateWriter.findInterruptedCompletions(completionStaleBefore());
        List<AiStoryStateWriter.ChoiceResultWork> choiceResults =
                stateWriter.findInterruptedChoiceResults(staleBefore());
        choiceResults.forEach(w -> startChoiceResult(w.memberId(), w.seasonPk(), w.pointId(), w.order(), w.round()));
        generations.forEach(w -> executor.execute(
                () -> generate(w.memberId(), w.seasonPk(), w.order(), w.contentPk(), w.round())));
        completions.forEach(w -> executor.execute(
                () -> runCompletion(w.memberId(), w.seasonPk(), w.order(), w.contentPk(), w.round())));
        if (!generations.isEmpty() || !completions.isEmpty() || !choiceResults.isEmpty()) {
            log.info("resuming interrupted story work: generations={}, completions={}, choiceResults={}",
                    generations.size(), completions.size(), choiceResults.size());
        }
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

    // ---------------------------------------------------------------- 내부 헬퍼

    /**
     * 생성 한 단계. AI가 실패를 돌려주거나 Spring 검증이 결과를 버리면 1회 다시 시도한다. 시도마다 job 키가 달라 다시
     * 시도하면 새 job이 되고, 같은 단계·시도를 다시 돌리면(재시작) 같은 job을 이어서 본다.
     */
    private <T> T callWithRetry(String operation, StoryRun run, Function<GenerationCall, T> call,
                                Consumer<T> validate) {
        run.recorder().record(operation, null, false);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                GenerationCall generationCall = new GenerationCall(run.key(operation, attempt),
                        progress -> run.recorder().record(operation, progress.jobId(), progress.regenerating()));
                T response = call.apply(generationCall);
                validate.accept(response);
                return response;
            } catch (AiStoryInvalidResponseException | AiServiceException e) {
                last = e;
                log.warn("story {} attempt {} failed: {}", operation, attempt, e.getMessage());
            }
        }
        throw new AiStoryGenerationFailedException(operation, last);
    }

    /** 한 번의 생성·완료 실행: AI job 키 접두사와 진행 상태 기록. */
    private record StoryRun(String keyBase, StepRecorder recorder) {

        String key(String operation, int attempt) {
            return keyBase + ":" + operation + ":a" + attempt;
        }
    }

    /** 진행 중인 단계(operation), 그 단계의 AI job, 다시 쓰는 중인지 저장한다. */
    @FunctionalInterface
    private interface StepRecorder {
        StepRecorder NONE = (stage, jobId, regenerating) -> {
        };

        void record(String stage, UUID jobId, boolean regenerating);
    }

    /** luvin:{AI 시즌 id}:c{회차 row id}:{종류}{회차 번호}. 회차 row id가 들어가 개발용 초기화 뒤 새 row와 겹치지 않는다. */
    private static String jobKeyBase(AiStorySnapshot snapshot, Long contentPk, String kind, int round) {
        return "luvin:" + snapshot.seasonId() + ":c" + contentPk + ":" + kind + round;
    }

    /** 회차 생성이 이 시각보다 먼저 시작한 채 끝나지 않았으면(서버 재시작 등) 멈춘 것으로 본다. */
    private LocalDateTime staleBefore() {
        return LocalDateTime.now().minus(properties.generationStaleAfter());
    }

    /**
     * 회차 완료는 FastAPI 호출이 최대 3번(정리·쪽지·10화 리포트)이고 각각 1회 재시도하므로, 그 최대 대기 시간이 지나도록
     * 끝나지 않아야 멈춘 것으로 본다. 더 짧으면 느리게 진행 중인 완료를 failed로 보여 재시도가 중복 완료를 시작한다.
     */
    private LocalDateTime completionStaleBefore() {
        Duration worstCase = properties.readTimeout().multipliedBy((long) COMPLETION_CALLS * MAX_ATTEMPTS);
        Duration staleAfter = worstCase.compareTo(properties.generationStaleAfter()) > 0
                ? worstCase : properties.generationStaleAfter();
        return LocalDateTime.now().minus(staleAfter);
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
                stateWriter.recentFeedbacks(snapshot.seasonPk()), stateWriter.recentLikedMyMessages(snapshot.seasonPk()),
                base.styleSamples());
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

    /** 완료한 회차의 정리 단계 요약. 아직 완료하지 않았으면 null. */
    private static String episodeSummaryOf(AiStorySnapshot.Content content) {
        if (content.summaryJson() == null) {
            return null;
        }
        Object summary = AiStoryJson.read(content.summaryJson(), Map.class).get("episodeSummary");
        return summary == null || summary.toString().isBlank() ? null : summary.toString();
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
    /**
     * 이전 회차에서 이미 쓴 장면·소재(본문 입력 usedMaterials, 최대 100개). 장면 이름·회차 제목·장면 전환에 더해, 완료한
     * 회차는 정리 단계 요약의 첫 문장을 "N화: …"로 넣는다 — 장면 이름만으로는 실제로 쓴 소재(예: 축제 포스터 정하기)가
     * 드러나지 않는다. 요약은 그 회차에 실제로 있었던 일이라 완료하지 않은 사건을 완료했다고 적지 않는다.
     */
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
                    String summary = episodeSummaryOf(c);
                    if (summary != null) {
                        materials.add(c.order() + "화: " + firstSentence(summary));
                    }
                });
        return materials.stream().distinct().limit(USED_MATERIALS_MAX).toList();
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
    /**
     * 생성 결과를 앱용 블록으로 바꾼다. 설계 순서대로 앞에서부터, 재료(본문 블록·인터뷰)가 있는 데까지만 붙인다 — 블록 단위
     * 생성 중에는 확정된 블록만 와서 그 앞까지만 만든다. 이미 만든 블록(메시지 id·순번)은 다시 만들지 않고 그 뒤를 잇는다.
     */
    private record BlockAssembly(AiEpisodePlan plan, AiStorySnapshot snapshot, AiStoryContractMapper mapper) {

        List<AiSceneBlockView> append(List<AiSceneBlockView> built, List<StoryTypes.GeneratedBlock> generatedBlocks,
                                      Map<String, List<StoryTypes.Message>> interviews) {
            Map<String, StoryTypes.GeneratedBlock> generated = new HashMap<>();
            generatedBlocks.forEach(b -> generated.put(b.id(), b));
            List<AiSceneBlockView> views = new ArrayList<>(built);
            int[] sequence = {1 + views.stream().mapToInt(v -> v.messages() == null ? 0 : v.messages().size()).sum()};
            for (int i = views.size(); i < plan.blocks().size(); i++) {
                AiSceneBlockView view = toView(plan.blocks().get(i), generated, interviews, sequence);
                if (view == null) {
                    break;
                }
                views.add(view);
            }
            return views;
        }

        /** 재료가 아직 없으면 null. */
        private AiSceneBlockView toView(BlockPlan blockPlan, Map<String, StoryTypes.GeneratedBlock> generated,
                                        Map<String, List<StoryTypes.Message>> interviews, int[] sequence) {
            String type = blockPlan.type().wireValue();
            if (blockPlan.type() == AiSceneBlockType.INTERVIEW) {
                List<StoryTypes.Message> messages = interviews.get(mapper.contractId(blockPlan.focusBreadId()));
                return messages == null ? null : new AiSceneBlockView(blockPlan.id(), type,
                        mapper.participants(blockPlan.participantIds()), mapper.messageViews(messages, sequence),
                        null, null, null, null, new AiSceneBlockView.SceneInterview(blockPlan.focusBreadId()));
            }
            StoryTypes.GeneratedBlock block = generated.get(blockPlan.id());
            if (block == null) {
                return null;
            }
            return switch (blockPlan.type()) {
                case CHOICE -> {
                    Map<String, String> memories = new HashMap<>();
                    if (block.candidates() != null) {
                        block.candidates().forEach(c -> memories.put(c.breadId(), c.memory()));
                    }
                    List<AiSceneBlockView.SceneChoiceCandidate> candidates = blockPlan.choice().candidateIds().stream()
                            .map(id -> new AiSceneBlockView.SceneChoiceCandidate(id,
                                    memories.getOrDefault(mapper.contractId(id), memoryLine(snapshot, id))))
                            .toList();
                    yield new AiSceneBlockView(blockPlan.id(), type, null, null, null, null,
                            new AiSceneBlockView.SceneChoice(blockPlan.choice().pointId(), blockPlan.choice().question(),
                                    candidates),
                            null, null);
                }
                case TRANSITION -> new AiSceneBlockView(blockPlan.id(), type, null, null, block.transitionText(), null,
                        null, null, null);
                default -> {
                    AiSceneBlockView.SceneMessenger messenger = blockPlan.type() == AiSceneBlockType.MESSENGER
                            ? new AiSceneBlockView.SceneMessenger(blockPlan.messengerKind(), blockPlan.roomName()) : null;
                    yield new AiSceneBlockView(blockPlan.id(), type, mapper.participants(blockPlan.participantIds()),
                            mapper.messageViews(block.messages(), sequence), null, messenger, null, null, null);
                }
            };
        }
    }

    private static List<AiSceneBlockView> readBlocks(String blocksJson) {
        return blocksJson == null ? List.of() : AiStoryJson.read(blocksJson, BLOCK_LIST);
    }

    private static AiStoryIntroView introView(StoryTypes.Intro intro) {
        return new AiStoryIntroView(intro.title(), intro.episodeName(), intro.situation());
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
        List<AiStoryFeedbackView> feedbacks = stateWriter.findFeedbacks(snapshot.seasonPk(), content.order()).stream()
                .map(f -> new AiStoryFeedbackView(f.getEpisodeOrder(), f.getBlockId(), f.getMessageId(), f.getText()))
                .toList();
        List<UUID> likedMessageIds = stateWriter.findLikedMessageIds(snapshot.seasonPk(), content.order());
        AiStoryIntroView intro = AiStoryJson.read(content.introJson(), AiStoryIntroView.class);
        AiStageIntroView stageIntro = AiStoryJson.read(content.stageIntroJson(), AiStageIntroView.class);
        if (content.status() != AiStoryContentStatus.READY) {
            // 블록 단위 생성: 생성 중(또는 중간 실패)에도 지금까지 확정된 블록을 준다. 아직 없으면 null.
            List<AiSceneBlockView> partial = content.blocksJson() == null ? null : readBlocks(content.blocksJson());
            AiGenerationProgressView progress = content.status() == AiStoryContentStatus.GENERATING
                    ? new AiGenerationProgressView(content.generationStage(), content.generationRegenerating(),
                    partial == null ? 0 : partial.size(), content.totalBlocks())
                    : null;
            return new AiEpisodeContentView(content.order(), status, content.errorCode(), intro, stageIntro, partial,
                    content.completed(), feedbacks, likedMessageIds, progress, content.revision());
        }
        return new AiEpisodeContentView(content.order(), status, null, intro, stageIntro,
                blocksWithChoiceResult(snapshot, content), content.completed(), feedbacks, likedMessageIds, null,
                content.revision());
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
        snapshot.choice(pointId).filter(AiStorySnapshot.Choice::resultReady).ifPresent(choice -> {
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
                .map(c -> new AiStoryStateView.ChoiceHistory(c.pointId(), c.episodeOrder(), c.breadId(), c.accepted(),
                        choiceStatus(c).status()))
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
                choices, breads, List.copyOf(unlocked), properties.episodeTokenCost(), completionOf(snapshot),
                snapshot.choices().stream()
                        .map(this::choiceStatus)
                        .filter(c -> !AiChoiceStatusView.READY.equals(c.status()))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * 진행 중이거나 실패한 회차 완료. 없으면 null. 완료 중인 채 오래 멈춘 것(서버 재시작 등)은 failed로 보여 앱이 다시
     * 요청하게 한다 — 그 요청이 완료를 새로 시작한다.
     */
    private AiStoryStateView.Completion completionOf(AiStorySnapshot snapshot) {
        LocalDateTime staleBefore = completionStaleBefore();
        return snapshot.contents().stream()
                .filter(c -> !c.completed() && c.completionRequestedAt() != null)
                .findFirst()
                .map(c -> c.completing() && !c.completionRequestedAt().isBefore(staleBefore)
                        ? new AiStoryStateView.Completion(c.order(), AiStoryStateView.Completion.COMPLETING, null,
                                new AiGenerationProgressView(c.completionStage(), c.completionRegenerating()))
                        : new AiStoryStateView.Completion(c.order(), AiStoryStateView.Completion.FAILED,
                                c.completing() ? AiStoryGenerationFailedException.CODE : c.completionErrorCode(), null))
                .orElse(null);
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
