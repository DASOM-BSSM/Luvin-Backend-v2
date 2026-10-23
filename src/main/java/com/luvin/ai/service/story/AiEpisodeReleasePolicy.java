package com.luvin.ai.service.story;

import com.luvin.ai.domain.AiSeason;
import org.springframework.stereotype.Component;

/**
 * 회차 공개 가능 여부. 테스트 단계라 지금은 항상 공개한다 — 주 1회 공개를 붙일 때 이 메서드만 바꾼다.
 */
@Component
public class AiEpisodeReleasePolicy {

    public boolean isReleased(AiSeason season, int order) {
        return true;
    }
}
