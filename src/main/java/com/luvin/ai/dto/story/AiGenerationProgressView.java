package com.luvin.ai.dto.story;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 진행 중인 AI 생성의 단계. 회차 생성(GET content의 generating)과 회차 완료(story.completion의 completing)에 붙는다.
 * regenerating이면 AI가 첫 시도를 검증에서 버리고 다시 쓰는 중이다 — 앱은 "문장을 다듬고 있어요" 같은 문구로 보여준다.
 * 실패가 아니며 생성은 계속된다.
 */
public record AiGenerationProgressView(
        /** 회차 생성: stage-intro | episode | interview. 회차 완료: summarize | episode-note | season-report. 시작 직전이면 null */
        @Schema(types = {"string", "null"},
                allowableValues = {"stage-intro", "episode", "interview", "summarize", "episode-note", "season-report"})
        String stage,
        boolean regenerating
) {
}
