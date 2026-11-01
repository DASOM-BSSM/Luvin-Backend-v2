package com.luvin.ai.client.story;

import com.luvin.ai.client.story.StoryTypes.GeneratedBlock;

import java.util.List;

/**
 * 블록 단위 생성(episode-stream) 중 지금까지 확정된 부분(GET /luvin/generation-jobs/{jobId}의 partial). blocks는 설계 순서대로만
 * 늘어나고, 한 번 나온 블록(id·문구·메시지 id)과 intro는 같은 job·이어 쓰기 job에서도 바뀌지 않는다.
 *
 * @param intro                 첫 블록이 확정되면 값이 있다
 * @param blocks                확정된 블록 전체(앞에서부터)
 * @param lastConfirmedSequence 마지막으로 확정된 순번(무대 소개 0, design[i]는 i+1). 아직 없으면 null
 */
public record EpisodePartial(StoryTypes.Intro intro, List<GeneratedBlock> blocks, Integer lastConfirmedSequence) {

    public List<GeneratedBlock> blocks() {
        return blocks == null ? List.of() : blocks;
    }
}
