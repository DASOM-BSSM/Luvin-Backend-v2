package com.luvin.ai.controller;

import com.luvin.ai.dto.story.AiEpisodeUnlockAllView;
import com.luvin.ai.dto.story.AiSeasonResetView;
import com.luvin.ai.service.story.AiSeasonResetWriter;
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
@Tag(name = "개발 전용", description = "app.dev-tools.enabled=true인 환경(dev·staging)에서만 존재한다. 운영에는 경로가 없다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev/ai/seasons")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class AiStoryDevController {

    private final AiSeasonResetWriter resetWriter;
    private final AiStoryService storyService;

    @Operation(summary = "AI 시즌 초기화(시즌 삭제, 토큰 환불)", description = """
            테스트용. 앱의 시즌 초기화 버튼은 정식 경로 `POST /api/ai/seasons/reset`(환불 없음)을 쓴다.
            이 경로는 그와 같고, 쓴 토큰을 돌려주는 것만 다르다.

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
        AiSeasonResetWriter.Result result = resetWriter.deleteAllSeasons(SecurityUtils.getCurrentUserId(), true);
        return ApiResponse.ok(new AiSeasonResetView(result.deletedSeasons(), result.refundedTokens(),
                result.tokenBalance(), result.refundedBreadTokens(), result.breadTokenBalance()));
    }

    @Operation(summary = "4~10화 한 번에 열기(토큰·하루 대기 없음)", description = """
            시연·테스트용. 지금 시즌의 4~10화 중 아직 안 연 회차를 **토큰 차감 없이** 모두 연다.
            소금빵 하루 대기(다음 자정, 409 `EPISODE_COOLDOWN`)도 걸리지 않는다 — 이미 연 회차는 대기 판정을 하지 않는다.

            - 이전 회차 완료 조건은 그대로다. 1화부터 차례로 플레이해야 다음 회차 본문을 받는다.
            - 열고 나면 `GET /api/ai/seasons/me`의 `story.unlockedOrders`에 4~10이 들어가고 `story.nextUnlockAt`은 null.
            - 다음 회차의 이전 회차가 이미 끝나 있으면 바로 미리 생성하고, 이후에도 회차를 끝낼 때마다 다음 회차를 미리 생성한다.
            - 연 기록의 쓴 토큰은 0이라 시즌 초기화(개발 전용 포함) 때 환불되는 토큰도 없다.
            - 여러 번 불러도 같은 결과(이미 연 회차는 건너뛴다). 시즌이 없으면 404 `SEASON_NOT_FOUND`, 끝난 시즌은 409.
            """)
    @PostMapping("/current/story/unlock-all")
    public ApiResponse<AiEpisodeUnlockAllView> unlockAllEpisodes() {
        return ApiResponse.ok(new AiEpisodeUnlockAllView(
                storyService.unlockAllForDev(SecurityUtils.getCurrentUserId())));
    }
}
