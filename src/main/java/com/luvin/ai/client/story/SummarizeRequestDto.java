package com.luvin.ai.client.story;

import java.util.List;
import java.util.UUID;

/** 회차 완료 정리. script는 그 회차에서 실제로 재생된 대사(선택 결과 장면 포함) 순서대로다. */
public record SummarizeRequestDto(StoryContextDto context, int order, List<ScriptLine> script) {

    public record ScriptLine(String blockId, String blockType, UUID speakerId, String speakerName, String text) {
    }
}
