package com.luvin.ai.controller;

import com.luvin.ai.service.story.AiStoryService;
import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트용 개발 전용 API. app.dev-tools.enabled=true일 때만 빈이 등록된다 — 운영(기본값 false)에서는 경로 자체가 없다.
 */
@Tag(name = "개발 전용", description = "app.dev-tools.enabled=true인 환경에서만 존재")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/ai/seasons")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class AiStoryDevController {

    private final AiStoryService storyService;

    @Operation(summary = "현재 시즌 회차 상태 초기화", description = "선택 기록·관계 기록·회차 콘텐츠·회차 쪽지·리포트를 지우고 1화 전으로 되돌린다.")
    @PostMapping("/current/story/reset")
    public ApiResponse<Void> resetStory() {
        storyService.resetCurrentSeason(SecurityUtils.getCurrentUserId());
        return ApiResponse.okMessage("시즌 회차 상태를 초기화했습니다.");
    }
}
