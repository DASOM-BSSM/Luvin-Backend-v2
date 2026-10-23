package com.luvin.ai.client.story;

import com.luvin.ai.client.AiServiceClient;
import com.luvin.ai.config.AiStoryConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * FastAPI story endpoint 호출(공통 데이터 계약 5장의 경로·camelCase 형식). 생성 호출은 부작용 없는 생성이라
 * Idempotency-Key는 호출마다 새로 만든다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.story.client", havingValue = "http")
public class AiStoryHttpClient implements AiStoryClient {

    static final String STAGE_INTRO_PATH = "/luvin/stage-intro";
    static final String EPISODE_PATH = "/luvin/episode";
    static final String CHOICE_RESULT_PATH = "/luvin/choice-result";
    static final String INTERVIEW_PATH = "/luvin/interview";
    static final String SUMMARIZE_PATH = "/luvin/summarize";
    static final String EPISODE_NOTE_PATH = "/luvin/episode-note";
    static final String SEASON_REPORT_PATH = "/luvin/season-report";

    private final AiServiceClient aiServiceClient;
    private final RestClient storyRestClient;

    public AiStoryHttpClient(AiServiceClient aiServiceClient,
                             @Qualifier("aiStoryRestClient") RestClient storyRestClient) {
        this.aiServiceClient = aiServiceClient;
        this.storyRestClient = storyRestClient;
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

    private <T> T post(String path, String ownerSubject, Object request, Class<T> type) {
        return aiServiceClient.post(storyRestClient, AiStoryConfig.STORY_OBJECT_MAPPER, path, ownerSubject,
                UUID.randomUUID(), request, type);
    }
}
