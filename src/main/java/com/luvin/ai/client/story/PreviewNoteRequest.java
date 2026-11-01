package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.ChoiceRecord;
import com.luvin.ai.client.story.StoryTypes.GenerationContext;
import com.luvin.ai.client.story.StoryTypes.RelationRecord;
import com.luvin.ai.client.story.StoryTypes.SceneBlock;

import java.util.List;

/**
 * 회차 쪽지 미리 만들기(task episode-note-preview, PreviewNoteInput). 회차를 끝내기 전에 만든다 — 정리 단계(5-5) 결과 없이
 * 이번 회차 블록(선택 회차면 선택 결과·거절 뒤 대체 장면 포함)으로 쓴다. relations와 sender.statusText는 이전 회차까지
 * 확정된 값, sender.speechLevel은 이번 회차에 쓴 말투다. 응답은 기존 쪽지 응답({@link EpisodeNoteResponse})의 body다.
 */
public record PreviewNoteRequest(GenerationContext context, int order, String settingScene, List<SceneBlock> blocks,
                                 ChoiceRecord choice, List<RelationRecord> relations, EpisodeNoteRequest.Sender sender,
                                 List<String> previousNoteOpenings) {
}
