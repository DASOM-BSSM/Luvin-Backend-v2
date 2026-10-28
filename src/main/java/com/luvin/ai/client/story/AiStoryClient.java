package com.luvin.ai.client.story;

/**
 * 러빈지옥 장면 블록 회차 생성용 FastAPI endpoint(공통 데이터 계약 5장). Spring은 Gemini를 직접 부르지 않고
 * 이 인터페이스로만 생성을 요청한다. app.ai.story.client=stub(기본)이면 {@link AiStoryStubClient}가,
 * http면 {@link AiStoryHttpClient}가 쓰인다. 오류는 기존 AiServiceClient와 같은 AiServiceException 계열로 던진다.
 *
 * {@link GenerationCall}을 받는 메서드는 비동기 생성 job(POST /luvin/generation-jobs)으로 부르고 끝날 때까지 기다린다.
 * 기본 구현은 동기 메서드를 그대로 부른다(stub). 호출부는 백그라운드 스레드에서 부른다.
 */
public interface AiStoryClient {

    StageIntroResponse stageIntro(String ownerSubject, StageIntroRequest request);

    EpisodeResponse writeEpisode(String ownerSubject, EpisodeRequest request);

    ChoiceResultResponse writeChoiceResult(String ownerSubject, ChoiceResultRequest request);

    InterviewResponse writeInterview(String ownerSubject, InterviewRequest request);

    SummarizeResponse summarize(String ownerSubject, SummarizeRequest request);

    EpisodeNoteResponse writeEpisodeNote(String ownerSubject, EpisodeNoteRequest request);

    SeasonReportResponse writeSeasonReport(String ownerSubject, SeasonReportRequest request);

    default StageIntroResponse stageIntro(String ownerSubject, StageIntroRequest request, GenerationCall call) {
        return stageIntro(ownerSubject, request);
    }

    default EpisodeResponse writeEpisode(String ownerSubject, EpisodeRequest request, GenerationCall call) {
        return writeEpisode(ownerSubject, request);
    }

    default ChoiceResultResponse writeChoiceResult(String ownerSubject, ChoiceResultRequest request,
                                                   GenerationCall call) {
        return writeChoiceResult(ownerSubject, request);
    }

    default InterviewResponse writeInterview(String ownerSubject, InterviewRequest request, GenerationCall call) {
        return writeInterview(ownerSubject, request);
    }

    default SummarizeResponse summarize(String ownerSubject, SummarizeRequest request, GenerationCall call) {
        return summarize(ownerSubject, request);
    }

    default EpisodeNoteResponse writeEpisodeNote(String ownerSubject, EpisodeNoteRequest request, GenerationCall call) {
        return writeEpisodeNote(ownerSubject, request);
    }

    default SeasonReportResponse writeSeasonReport(String ownerSubject, SeasonReportRequest request,
                                                   GenerationCall call) {
        return writeSeasonReport(ownerSubject, request);
    }
}
