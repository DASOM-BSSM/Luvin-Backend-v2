package com.luvin.ai.controller;

import com.luvin.ai.dto.story.AiSeasonResetView;
import com.luvin.ai.service.story.AiSeasonResetWriter;
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
@Tag(name = "개발 전용", description = "app.dev-tools.enabled=true인 환경(dev·staging)에서만 존재한다. 운영에는 경로가 없다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/ai/seasons")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class AiStoryDevController {

    private final AiSeasonResetWriter resetWriter;

    @Operation(summary = "AI 시즌 초기화(시즌 삭제)", description = """
            내 AI 시즌을 **시즌 행까지 모두 지운다**. 진행 중이든 완료했든 같고, 시즌이 여러 개면 전부 지운다.
            계정·설문 결과와 빵 유형·감정일기·공유방은 남는다.

            - 지우는 것: 회차 본문·선택 결과 장면·선택 기록·열린 회차·회차 완료 상태, 1:1 장면 좋아요·피드백(학습 입력 포함),
              회차 정리 기록·관계 기록·회차 쪽지·시즌 편지·최종 리포트, 출연진, 새 시즌 설정, 시즌 생성 입력
            - 그 시즌들에서 4~10화를 열며 쓴 토큰은 돌려준다(`refundedTokens`, 내역은 REWARD "러빈지옥 시즌 초기화 환불").
            - 초기화 전에 돌던 회차 생성·완료·선택 결과 생성은 시즌이 없어져 결과를 버린다.
            - 시즌이 없으면 아무것도 하지 않고 200(`deletedSeasons` 0).

            초기화 뒤: `GET /api/ai/seasons/me`는 404 `SEASON_NOT_FOUND` → `POST /api/ai/seasons/from-survey`
            (`surveyResultId`는 최신 설문 결과)로 새 시즌 → `GET /api/ai/seasons/episodes/1/content`로 1화부터 시작.
            """)
    @PostMapping("/current/story/reset")
    public ApiResponse<AiSeasonResetView> resetSeasons() {
        AiSeasonResetWriter.Result result = resetWriter.deleteAllSeasons(SecurityUtils.getCurrentUserId());
        return ApiResponse.ok(new AiSeasonResetView(result.deletedSeasons(), result.refundedTokens(),
                result.tokenBalance()));
    }
}
