package com.luvin.ai.client.story;

/**
 * 러빈지옥 장면 블록 회차 생성용 FastAPI endpoint(공통 데이터 계약 5장). Spring은 Gemini를 직접 부르지 않고
 * 이 인터페이스로만 생성을 요청한다. app.ai.story.client=stub(기본)이면 {@link AiStoryStubClient}가,
 * http면 {@link AiStoryHttpClient}가 쓰인다. 오류는 기존 AiServiceClient와 같은 AiServiceException 계열로 던진다.
 */
public interface AiStoryClient {

    StageIntroResponse stageIntro(String ownerSubject, StageIntroRequest request);

    EpisodeResponse writeEpisode(String ownerSubject, EpisodeRequest request);

    ChoiceResultResponse writeChoiceResult(String ownerSubject, ChoiceResultRequest request);

    InterviewResponse writeInterview(String ownerSubject, InterviewRequest request);

    SummarizeResponse summarize(String ownerSubject, SummarizeRequest request);

    EpisodeNoteResponse writeEpisodeNote(String ownerSubject, EpisodeNoteRequest request);

    SeasonReportResponse writeSeasonReport(String ownerSubject, SeasonReportRequest request);
}
