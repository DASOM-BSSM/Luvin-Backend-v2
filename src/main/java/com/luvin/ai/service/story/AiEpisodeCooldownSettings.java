package com.luvin.ai.service.story;

import com.luvin.ai.config.AiStoryProperties;
import com.luvin.ai.domain.AiStorySetting;
import com.luvin.ai.repository.AiStorySettingRepository;
import com.luvin.ai.service.exception.AiInputValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * 소금빵으로 4~10화를 열 때 기다리는 시간(앞 회차 완료 시각 기준). 재배포 없이 바꿀 수 있다.
 *
 * <p>ai_story_settings에 값이 있으면 그 값을, 없으면 app.ai.story.episode-unlock-cooldown(환경변수, 없으면 코드 기본 3일)을
 * 쓴다. DB 값은 {@link #CACHE_TTL} 동안 캐시한다 — 이 서버에서 바꾸면 바로, 다른 인스턴스에는 그 시간 안에 반영된다.
 */
@Component
public class AiEpisodeCooldownSettings {

    private static final Logger log = LoggerFactory.getLogger(AiEpisodeCooldownSettings.class);

    static final String KEY = "episode-unlock-cooldown";
    static final Duration MAX = Duration.ofDays(30);
    static final Duration CACHE_TTL = Duration.ofSeconds(30);
    public static final String INVALID_COOLDOWN = "INVALID_COOLDOWN";

    private final AiStorySettingRepository repository;
    private final AiStoryProperties properties;
    private volatile Cached cached;

    public AiEpisodeCooldownSettings(AiStorySettingRepository repository, AiStoryProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** 지금 적용되는 대기 시간. */
    public Duration current() {
        Duration override = override();
        return override != null ? override : properties.episodeUnlockCooldown();
    }

    public Setting setting() {
        return settingOf(override());
    }

    /**
     * 대기 시간을 바꾼다. "24h", "3d", "90m", "PT24H"처럼 쓴다. 0 이상 30일 이하만 받는다(0이면 대기 없음).
     * 이미 완료한 회차에도 바로 적용된다(완료 시각 + 새 대기 시간).
     */
    public Setting change(String raw) {
        Duration cooldown = parse(raw);
        AiStorySetting setting = repository.findById(KEY).orElse(null);
        if (setting == null) {
            setting = new AiStorySetting(KEY, cooldown.toString());
        } else {
            setting.change(cooldown.toString());
        }
        repository.save(setting);
        cached = Cached.of(cooldown);
        log.info("episode unlock cooldown changed: {}", cooldown);
        return settingOf(cooldown);
    }

    /** DB 값을 지워 애플리케이션 설정값으로 돌아간다. */
    public Setting reset() {
        if (repository.existsById(KEY)) {
            repository.deleteById(KEY);
        }
        cached = Cached.of(null);
        log.info("episode unlock cooldown reset to default: {}", properties.episodeUnlockCooldown());
        return settingOf(null);
    }

    private Setting settingOf(Duration override) {
        Duration fallback = properties.episodeUnlockCooldown();
        return new Setting(override != null ? override : fallback, override != null, fallback);
    }

    private Duration override() {
        Cached snapshot = cached;
        if (snapshot == null || snapshot.expired()) {
            snapshot = Cached.of(load());
            cached = snapshot;
        }
        return snapshot.override();
    }

    private Duration load() {
        return repository.findById(KEY).map(setting -> {
            try {
                return Duration.parse(setting.getValue());
            } catch (DateTimeParseException e) {
                log.warn("ignoring invalid {} setting: {}", KEY, setting.getValue());
                return null;
            }
        }).orElse(null);
    }

    static Duration parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiInputValidationException(INVALID_COOLDOWN, "대기 시간을 입력해주세요. 예: 24h, 3d");
        }
        Duration cooldown;
        try {
            cooldown = DurationStyle.detectAndParse(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new AiInputValidationException(INVALID_COOLDOWN, "대기 시간 형식이 올바르지 않습니다. 예: 24h, 3d");
        }
        if (cooldown.isNegative() || cooldown.compareTo(MAX) > 0) {
            throw new AiInputValidationException(INVALID_COOLDOWN, "대기 시간은 0 이상 30일 이하여야 합니다.");
        }
        return cooldown;
    }

    /**
     * @param cooldown   지금 적용되는 대기 시간
     * @param overridden DB 값(운영 중 바꾼 값)을 쓰는 중이면 true
     * @param fallback   DB 값이 없을 때 쓰는 애플리케이션 설정값
     */
    public record Setting(Duration cooldown, boolean overridden, Duration fallback) {
    }

    private record Cached(Duration override, long expiresAt) {
        static Cached of(Duration override) {
            return new Cached(override, System.nanoTime() + CACHE_TTL.toNanos());
        }

        boolean expired() {
            return System.nanoTime() - expiresAt > 0;
        }
    }
}
