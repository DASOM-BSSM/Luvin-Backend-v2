package com.luvin.ai.dto.story;

import com.luvin.ai.service.story.AiEpisodeCooldownSettings;

/** 소금빵 회차 열기 대기 시간 설정(개발 전용 경로). 기간은 ISO-8601(예: PT24H) 문자열이다. */
public record AiEpisodeCooldownSettingView(
        /** 지금 적용되는 대기 시간. */
        String cooldown,
        /** 지금 적용되는 대기 시간(초). */
        long cooldownSeconds,
        /** 운영 중 바꾼 값(DB)을 쓰는 중이면 true, 애플리케이션 설정값이면 false. */
        boolean overridden,
        /** DB 값이 없을 때 쓰는 애플리케이션 설정값(환경변수, 없으면 3일). */
        String defaultCooldown
) {
    public static AiEpisodeCooldownSettingView of(AiEpisodeCooldownSettings.Setting setting) {
        return new AiEpisodeCooldownSettingView(setting.cooldown().toString(), setting.cooldown().toSeconds(),
                setting.overridden(), setting.fallback().toString());
    }
}
