package com.luvin.ai.client.story;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.luvin.ai.client.AiServiceClient;
import com.luvin.ai.client.exception.AiContractViolationException;
import com.luvin.ai.client.exception.AiSelectionConflictException;
import com.luvin.ai.client.exception.AiServerException;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.config.AiStoryConfig;
import com.luvin.ai.config.AiStoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * FastAPI story endpoint 호출(공통 데이터 계약 5장의 경로·camelCase 형식).
 *
 * {@link GenerationCall}을 받는 호출은 app.ai.story.generation-mode=jobs(기본)면 비동기 생성 job으로 부른다: 접수(202) →
 * 끝날 때까지 상태 조회 → succeeded면 result(기존 동기 응답과 같은 JSON)를 돌려준다. 접수에는 Spring 논리 작업 키를
 * Idempotency-Key로 보내, 같은 키로 다시 접수하면 같은 job을 이어서 본다. SSE는 쓰지 않고 조회만 한다(백그라운드
 * 스레드에서 부르므로 요청 스레드를 붙잡지 않는다). 동기 메서드와 generation-mode=sync는 기존 동기 endpoint를 부르고,
 * 그때 Idempotency-Key는 호출마다 새로 만든다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.story.client", havingValue = "http")
public class AiStoryHttpClient implements AiStoryClient {

    private static final Logger log = LoggerFactory.getLogger(AiStoryHttpClient.class);

    static final String STAGE_INTRO_PATH = "/luvin/stage-intro";
    static final String EPISODE_PATH = "/luvin/episode";
    static final String CHOICE_RESULT_PATH = "/luvin/choice-result";
    static final String INTERVIEW_PATH = "/luvin/interview";
    static final String SUMMARIZE_PATH = "/luvin/summarize";
    static final String EPISODE_NOTE_PATH = "/luvin/episode-note";
    static final String SEASON_REPORT_PATH = "/luvin/season-report";
    static final String JOBS_PATH = "/luvin/generation-jobs";
    static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    /** 같은 논리 작업에서 이미 실패한 job(또는 다른 입력의 409)을 건너뛰며 새 키를 쓰는 최대 횟수. */
    private static final int MAX_KEY_SUFFIX = 5;

    private final AiServiceClient aiServiceClient;
    private final RestClient storyRestClient;
    private final AiStoryProperties properties;

    public AiStoryHttpClient(AiServiceClient aiServiceClient,
                             @Qualifier("aiStoryRestClient") RestClient storyRestClient,
                             AiStoryProperties properties) {
        this.aiServiceClient = aiServiceClient;
        this.storyRestClient = storyRestClient;
        this.properties = properties;
    }

    @Override
    public StageIntroResponse stageIntro(String ownerSubject, StageIntroRequest request) {
        return post(STAGE_INTRO_PATH, ownerSubject, request, StageIntroResponse.class);
    }

    @Override
    public EpisodeResponse writeEpisode(String ownerSubject, EpisodeRequest request) {
        return post(EPISODE_PATH, ownerSubject, request, EpisodeResponse.class);
    }

    @Override
    public ChoiceResultResponse writeChoiceResult(String ownerSubject, ChoiceResultRequest request) {
        return post(CHOICE_RESULT_PATH, ownerSubject, request, ChoiceResultResponse.class);
    }

    @Override
    public InterviewResponse writeInterview(String ownerSubject, InterviewRequest request) {
        return post(INTERVIEW_PATH, ownerSubject, request, InterviewResponse.class);
    }

    @Override
    public SummarizeResponse summarize(String ownerSubject, SummarizeRequest request) {
        return post(SUMMARIZE_PATH, ownerSubject, request, SummarizeResponse.class);
    }

    @Override
    public EpisodeNoteResponse writeEpisodeNote(String ownerSubject, EpisodeNoteRequest request) {
        return post(EPISODE_NOTE_PATH, ownerSubject, request, EpisodeNoteResponse.class);
    }

    @Override
    public SeasonReportResponse writeSeasonReport(String ownerSubject, SeasonReportRequest request) {
        return post(SEASON_REPORT_PATH, ownerSubject, request, SeasonReportResponse.class);
    }

    @Override
    public StageIntroResponse stageIntro(String ownerSubject, StageIntroRequest request, GenerationCall call) {
        return properties.usesJobs() ? runJob("stage-intro", ownerSubject, request, StageIntroResponse.class, call)
                : stageIntro(ownerSubject, request);
    }

    @Override
    public EpisodeResponse writeEpisode(String ownerSubject, EpisodeRequest request, GenerationCall call) {
        return properties.usesJobs() ? runJob("episode", ownerSubject, request, EpisodeResponse.class, call)
                : writeEpisode(ownerSubject, request);
    }

    @Override
    public ChoiceResultResponse writeChoiceResult(String ownerSubject, ChoiceResultRequest request,
                                                  GenerationCall call) {
        return properties.usesJobs() ? runJob("choice-result", ownerSubject, request, ChoiceResultResponse.class, call)
                : writeChoiceResult(ownerSubject, request);
    }

    @Override
    public InterviewResponse writeInterview(String ownerSubject, InterviewRequest request, GenerationCall call) {
        return properties.usesJobs() ? runJob("interview", ownerSubject, request, InterviewResponse.class, call)
                : writeInterview(ownerSubject, request);
    }

    @Override
    public SummarizeResponse summarize(String ownerSubject, SummarizeRequest request, GenerationCall call) {
        return properties.usesJobs() ? runJob("summarize", ownerSubject, request, SummarizeResponse.class, call)
                : summarize(ownerSubject, request);
    }

    @Override
    public EpisodeNoteResponse writeEpisodeNote(String ownerSubject, EpisodeNoteRequest request, GenerationCall call) {
        return properties.usesJobs() ? runJob("episode-note", ownerSubject, request, EpisodeNoteResponse.class, call)
                : writeEpisodeNote(ownerSubject, request);
    }

    @Override
    public SeasonReportResponse writeSeasonReport(String ownerSubject, SeasonReportRequest request,
                                                  GenerationCall call) {
        return properties.usesJobs() ? runJob("season-report", ownerSubject, request, SeasonReportResponse.class, call)
                : writeSeasonReport(ownerSubject, request);
    }

    private <T> T post(String path, String ownerSubject, Object request, Class<T> type) {
        return aiServiceClient.post(storyRestClient, AiStoryConfig.STORY_OBJECT_MAPPER, path, ownerSubject,
                UUID.randomUUID(), request, type);
    }

    /**
     * job을 접수하고 끝날 때까지 조회한다. 조회 중 네트워크 오류·5xx는 다음 조회에서 다시 본다(job은 AI에서 계속 진행된다).
     * failed면 AiServiceException(errorCode = AI의 GENERATION_*)으로 던진다.
     */
    private <T> T runJob(String task, String ownerSubject, Object input, Class<T> type, GenerationCall call) {
        GenerationJobResponse job = submit(task, ownerSubject, input, call.idempotencyKey());
        call.report(progressOf(job));
        GenerationJobResponse reported = job;
        long deadline = System.nanoTime() + properties.jobWaitTimeout().toNanos();
        while (!job.terminal()) {
            if (System.nanoTime() > deadline) {
                throw new AiServerException("[" + JOBS_PATH + " " + task + "] " + properties.jobWaitTimeout()
                        + " 안에 끝나지 않았습니다.", (String) null);
            }
            sleep(properties.jobPollInterval());
            try {
                job = aiServiceClient.get(storyRestClient, AiStoryConfig.STORY_OBJECT_MAPPER, JOBS_PATH + "/{jobId}",
                        JOBS_PATH + "/" + job.jobId(), ownerSubject, GenerationJobResponse.class);
            } catch (AiServerException transientFailure) {
                continue;
            }
            if (!Objects.equals(reported.status(), job.status()) || reported.attempt() != job.attempt()) {
                call.report(progressOf(job));
                reported = job;
            }
        }
        if (GenerationJobResponse.FAILED.equals(job.status())) {
            throw new AiServiceException("[" + JOBS_PATH + " " + task + "] 생성 실패: " + job.errorCode(),
                    job.errorCode(), null);
        }
        try {
            return AiStoryConfig.STORY_OBJECT_MAPPER.treeToValue(job.result(), type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new AiContractViolationException("[" + JOBS_PATH + " " + task + "] result 형식이 계약과 다릅니다.",
                    (String) null);
        }
    }

    /**
     * 같은 키로 접수하면 AI는 기존 job을 돌려준다. 그 job이 이미 실패했거나(실패 job은 같은 키로 다시 실행되지 않는다) 같은
     * 키에 다른 입력이라 409면, 키 뒤에 번호를 붙여 새 작업으로 접수한다. 새로 만든 job은 접수 시점에 failed일 수 없으므로
     * 접수 응답이 failed면 이전 시도의 job이다.
     */
    private GenerationJobResponse submit(String task, String ownerSubject, Object input, String key) {
        for (int suffix = 0; ; suffix++) {
            String attemptKey = suffix == 0 ? key : key + ":n" + suffix;
            try {
                GenerationJobResponse job = aiServiceClient.post(storyRestClient, AiStoryConfig.STORY_OBJECT_MAPPER,
                        JOBS_PATH, ownerSubject, attemptKey, Map.of("task", task, "input", input),
                        GenerationJobResponse.class);
                if (!GenerationJobResponse.FAILED.equals(job.status()) || suffix >= MAX_KEY_SUFFIX) {
                    return job;
                }
                log.info("story {} job already failed for key, submitting a new one: errorCode={}", task,
                        job.errorCode());
            } catch (AiSelectionConflictException conflict) {
                if (!IDEMPOTENCY_CONFLICT.equals(conflict.getAiErrorCode()) || suffix >= MAX_KEY_SUFFIX) {
                    throw conflict;
                }
                log.info("story {} job key was used with a different input, submitting a new one", task);
            }
        }
    }

    private static GenerationProgress progressOf(GenerationJobResponse job) {
        return new GenerationProgress(job.jobId(), job.status(), job.attempt());
    }

    private static void sleep(Duration interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServerException("생성 job 대기 중 중단됨", e);
        }
    }
}
