package com.luvin.ai.controller;

import com.luvin.ai.dto.story.AiEpisodeCooldownChangeRequest;
import com.luvin.ai.dto.story.AiEpisodeCooldownSettingView;
import com.luvin.ai.service.story.AiEpisodeCooldownSettings;
import com.luvin.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 러빈지옥 회차 공개 주기를 재배포 없이 바꾸는 개발 전용 API. app.dev-tools.enabled=true일 때만 빈이 등록된다 — 운영에는 경로가
 * 없다. 서버 전체에 적용되는 값이라 로그인한 누구나 바꿀 수 있는 이 경로는 dev·staging에만 둔다.
 */
@Tag(name = "개발 전용", description = "app.dev-tools.enabled=true인 환경(dev·staging)에서만 존재한다. 운영에는 경로가 없다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/ai/story/settings/episode-unlock-cooldown")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class AiStorySettingsDevController {

    private final AiEpisodeCooldownSettings cooldownSettings;

    @Operation(summary = "소금빵 회차 열기 대기 시간 조회", description = """
            4~10화를 소금빵으로 열 때 앞 회차 완료 뒤 기다리는 시간. `overridden`이 true면 이 API로 바꾼 값을,
            false면 서버 설정값(`defaultCooldown`, 환경변수 `APP_AI_STORY_EPISODEUNLOCKCOOLDOWN`, 없으면 3일)을 쓰는 중이다.
            """)
    @GetMapping
    public ApiResponse<AiEpisodeCooldownSettingView> get() {
        return ApiResponse.ok(AiEpisodeCooldownSettingView.of(cooldownSettings.setting()));
    }

    @Operation(summary = "소금빵 회차 열기 대기 시간 변경(재배포 없이)", description = """
            서버 전체(모든 사용자·시즌)에 바로 적용된다. 이미 완료한 회차도 `완료 시각 + 새 대기 시간`으로 다시 계산되어
            `GET /api/ai/seasons/me`의 `story.nextUnlockAt`과 409 `EPISODE_COOLDOWN` 판정이 바뀐다.

            - `cooldown`: `24h`, `3d`, `90m`, `PT24H` 형식. 0 이상 30일 이하(0이면 대기 없음)
            - 형식이 틀리거나 범위를 벗어나면 400 `INVALID_COOLDOWN`
            - 식빵으로 여는 것과 1~3화(무료)는 영향이 없다
            """)
    @PutMapping
    public ApiResponse<AiEpisodeCooldownSettingView> change(@RequestBody AiEpisodeCooldownChangeRequest request) {
        return ApiResponse.ok(AiEpisodeCooldownSettingView.of(cooldownSettings.change(request.cooldown())));
    }

    @Operation(summary = "소금빵 회차 열기 대기 시간 되돌리기", description = """
            이 API로 바꾼 값을 지우고 서버 설정값(`defaultCooldown`)으로 돌아간다. 바꾼 적이 없어도 200.
            """)
    @DeleteMapping
    public ApiResponse<AiEpisodeCooldownSettingView> reset() {
        return ApiResponse.ok(AiEpisodeCooldownSettingView.of(cooldownSettings.reset()));
    }
}
