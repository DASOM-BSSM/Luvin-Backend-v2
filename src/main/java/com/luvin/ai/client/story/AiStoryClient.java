package com.luvin.ai.client.story;

import com.luvin.ai.client.dto.LetterResponseDto;
import com.luvin.ai.client.dto.ReportResponseDto;

/**
 * 장면 블록 회차 생성용 FastAPI endpoint. Spring은 Gemini를 직접 부르지 않고 이 인터페이스로만 생성을 요청한다.
 * 계약(경로·필드)이 AI 쪽과 확정되기 전에는 {@link AiStoryStubClient}로 흐름을 돌린다(app.ai.story.client=stub).
 * 오류는 기존 AiServiceClient와 같은 AiServiceException 계열로 던진다.
 */
public interface AiStoryClient {

    StageIntroResponseDto stageIntro(String ownerSubject, StageIntroRequestDto request);

    EpisodeWriteResponseDto writeEpisode(String ownerSubject, EpisodeWriteRequestDto request);

    StoryBlocksResponseDto writeChoiceResult(String ownerSubject, ChoiceResultRequestDto request);

    StoryBlocksResponseDto writeInterview(String ownerSubject, InterviewRequestDto request);

    SummarizeResponseDto summarize(String ownerSubject, SummarizeRequestDto request);

    LetterResponseDto writeEpisodeNote(String ownerSubject, EpisodeNoteRequestDto request);

    ReportResponseDto writeSeasonReport(String ownerSubject, SeasonReportRequestDto request);
}
