package com.luvin.ai.dto.story;

import java.util.List;

/** 개발 전용 4~10화 일괄 열기 결과. */
public record AiEpisodeUnlockAllView(
        /** 지금 시즌에서 열려 있는 4~10화(오름차순). 일괄 열기 전에 이미 연 회차도 들어 있다. */
        List<Integer> unlockedOrders
) {
}
