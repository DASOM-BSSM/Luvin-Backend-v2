package com.luvin.ai.client.story;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.function.Consumer;

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

    /** 2시즌부터 1화 생성 때 무대 소개 대신 부르는 새 시즌 설정(POST /luvin/season-design). */
    SeasonDesignResponse designSeason(String ownerSubject, SeasonDesignRequest request);

    EpisodeResponse writeEpisode(String ownerSubject, EpisodeRequest request);

    ChoiceResultResponse writeChoiceResult(String ownerSubject, ChoiceResultRequest request);

    InterviewResponse writeInterview(String ownerSubject, InterviewRequest request);

    SummarizeResponse summarize(String ownerSubject, SummarizeRequest request);

    EpisodeNoteResponse writeEpisodeNote(String ownerSubject, EpisodeNoteRequest request);

    SeasonReportResponse writeSeasonReport(String ownerSubject, SeasonReportRequest request);

    default StageIntroResponse stageIntro(String ownerSubject, StageIntroRequest request, GenerationCall call) {
        return stageIntro(ownerSubject, request);
    }

    default SeasonDesignResponse designSeason(String ownerSubject, SeasonDesignRequest request, GenerationCall call) {
        return designSeason(ownerSubject, request);
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

    /**
     * 5-2 본문을 블록 단위로 확정하며 생성한다(task episode-stream). input은 {@link EpisodeRequest}를 계약 JSON으로 바꾼
     * 것이고, 이어 쓰기 때는 처음 보낸 원문을 그대로 넘긴다. 확정된 부분이 늘 때마다 onPartial을 부른다.
     *
     * previousJobId가 있으면 그 job부터 본다: 아직 진행 중이면 이어서 기다리고, 성공했으면 그 결과를, 실패했으면 그 job에서
     * 이어 쓰는 새 job(resumeFromJobId)을 접수한다 — 이미 확정된 블록은 바뀌지 않는다.
     *
     * @throws AiStoryFeatureUnsupportedException 블록 단위 생성을 쓸 수 없다(기존 writeEpisode로 생성)
     * @throws AiStreamResumeRejectedException    이어 쓰기를 거부했다(처음부터 다시 생성)
     */
    default EpisodeResponse streamEpisode(String ownerSubject, JsonNode input, UUID previousJobId,
                                          GenerationCall call, Consumer<EpisodePartial> onPartial) {
        throw new AiStoryFeatureUnsupportedException("블록 단위 생성을 지원하지 않습니다.");
    }

    /**
     * 회차 쪽지를 회차를 끝내기 전에 미리 만든다(task episode-note-preview).
     *
     * @throws AiStoryFeatureUnsupportedException 미리 만들기를 쓸 수 없다(완료 때 기존 episode-note로 만든다)
     */
    default EpisodeNoteResponse previewEpisodeNote(String ownerSubject, PreviewNoteRequest request,
                                                   GenerationCall call) {
        throw new AiStoryFeatureUnsupportedException("쪽지 미리 만들기를 지원하지 않습니다.");
    }
}
