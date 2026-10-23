package com.luvin.ai.client.story;

import com.luvin.ai.client.AiServiceClient;
import com.luvin.ai.client.dto.LetterResponseDto;
import com.luvin.ai.client.dto.ReportResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * FastAPI story endpoint 호출. 경로는 아직 AI 쪽과 합의 전인 임시값이다(AI 저장소의 /v1 라우팅 규칙을 따름).
 * 생성 호출은 부작용 없는 생성이라 Idempotency-Key는 호출마다 새로 만든다.
 */
@Component
@ConditionalOnProperty(name = "app.ai.story.client", havingValue = "http")
public class AiStoryHttpClient implements AiStoryClient {

    static final String STAGE_INTRO_PATH = "/v1/story/stage-intro";
    static final String EPISODE_PATH = "/v1/story/episode";
    static final String CHOICE_RESULT_PATH = "/v1/story/choice-result";
    static final String INTERVIEW_PATH = "/v1/story/interview";
    static final String SUMMARIZE_PATH = "/v1/story/summarize";
    static final String EPISODE_NOTE_PATH = "/v1/story/episode-note";
    static final String SEASON_REPORT_PATH = "/v1/story/season-report";

    private final AiServiceClient aiServiceClient;
    private final RestClient storyRestClient;

    public AiStoryHttpClient(AiServiceClient aiServiceClient,
                             @Qualifier("aiStoryRestClient") RestClient storyRestClient) {
        this.aiServiceClient = aiServiceClient;
        this.storyRestClient = storyRestClient;
    }

    @Override
    public StageIntroResponseDto stageIntro(String ownerSubject, StageIntroRequestDto request) {
        return post(STAGE_INTRO_PATH, ownerSubject, request, StageIntroResponseDto.class);
    }

    @Override
    public EpisodeWriteResponseDto writeEpisode(String ownerSubject, EpisodeWriteRequestDto request) {
        return post(EPISODE_PATH, ownerSubject, request, EpisodeWriteResponseDto.class);
    }

    @Override
    public StoryBlocksResponseDto writeChoiceResult(String ownerSubject, ChoiceResultRequestDto request) {
        return post(CHOICE_RESULT_PATH, ownerSubject, request, StoryBlocksResponseDto.class);
    }

    @Override
    public StoryBlocksResponseDto writeInterview(String ownerSubject, InterviewRequestDto request) {
        return post(INTERVIEW_PATH, ownerSubject, request, StoryBlocksResponseDto.class);
    }

    @Override
    public SummarizeResponseDto summarize(String ownerSubject, SummarizeRequestDto request) {
        return post(SUMMARIZE_PATH, ownerSubject, request, SummarizeResponseDto.class);
    }

    @Override
    public LetterResponseDto writeEpisodeNote(String ownerSubject, EpisodeNoteRequestDto request) {
        return post(EPISODE_NOTE_PATH, ownerSubject, request, LetterResponseDto.class);
    }

    @Override
    public ReportResponseDto writeSeasonReport(String ownerSubject, SeasonReportRequestDto request) {
        return post(SEASON_REPORT_PATH, ownerSubject, request, ReportResponseDto.class);
    }

    private <T> T post(String path, String ownerSubject, Object request, Class<T> type) {
        return aiServiceClient.post(storyRestClient, path, ownerSubject, UUID.randomUUID(), request, type);
    }
}
