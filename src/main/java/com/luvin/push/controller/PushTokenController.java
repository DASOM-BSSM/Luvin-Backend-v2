package com.luvin.push.controller;

import com.luvin.common.response.ApiResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.push.dto.PushTokenDeleteRequest;
import com.luvin.push.dto.PushTokenRegisterRequest;
import com.luvin.push.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "푸시 토큰", description = PushTokenController.PUSH_CATALOG)
@RestController
@RequestMapping("/api/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    /** 서버가 보내는 푸시 목록(앱과 맞춘 계약). type 이름과 data 형식은 바꾸지 않는다. */
    static final String PUSH_CATALOG = """
            서버가 보내는 푸시(Expo). 알림을 누르면 앱은 `data`를 받는다. **type 이름과 data 형식은 앱과 맞춘 계약이라 바꾸지 않는다.**

            | type | 언제 | 누구에게 | data | 문구 |
            |---|---|---|---|---|
            | `QUESTION_CREATED` | 그룹원이 만든 그룹 질문이 준비됐을 때 | 같은 그룹의 다른 멤버 전원 | `{ type, roomId }` | "○○님이 질문을 만들었어요" |
            | `QUESTION_ANSWERED` | 그룹원이 질문에 답을 처음 올렸을 때(수정은 보내지 않음) | 같은 그룹의 다른 멤버 전원 | `{ type, roomId }` | "○○님이 답변을 올렸어요" |
            | `DIARY_COMMENT` | 내 일기(1시간 기분)에 댓글이 달렸을 때 | 그 일기 작성자 | `{ type, roomId, diaryId }` | "○○님이 내 기분에 댓글을 남겼어요" |

            - 공통
              - 본인이 한 행동은 본인에게 보내지 않는다.
              - 알림 제목은 그룹 이름, ○○는 그룹 닉네임(없으면 계정 닉네임)이다.
              - `roomId`·`diaryId`는 숫자다.
              - Android `channelId`는 `diary-activity`("감정일기 소식" 채널)다.
            - **발송 시간 제한(모든 푸시)**: 한국 시간(KST) 06:00~23:59에만 보낸다. 00:00~05:59에 생긴 알림은 보내지 않고 버린다(아침에 몰아서 보내지 않음).
            - 러빈지옥 회차 생성 완료 푸시(`EPISODE_READY`)는 보내지 않는다. 앱이 회차 공개 시각(`story.nextUnlockAt`)에 기기 알림을 직접 건다.
            """;

    private final PushTokenService pushTokenService;

    @Operation(summary = "기기 푸시 토큰 등록", description = """
            body `{ token, platform }` — token은 Expo 푸시 토큰(`ExponentPushToken[...]`), platform은 `ios` | `android`.
            같은 토큰은 덮어쓴다(다른 계정으로 등록돼 있던 토큰이면 이 계정으로 옮긴다). 기기 여러 대를 등록할 수 있다.
            로그인 직후·앱 시작 때 불러도 된다. 회원 탈퇴 때 서버가 지우고, Expo가 DeviceNotRegistered를 돌려준 토큰도 지운다.

            **오류**: 400 token이 Expo 푸시 토큰이 아니거나 platform이 ios·android가 아님
            """)
    @PostMapping
    public ApiResponse<Void> register(@Valid @RequestBody PushTokenRegisterRequest request) {
        pushTokenService.register(SecurityUtils.getCurrentUserId(), request.token(), request.platform());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "기기 푸시 토큰 삭제(로그아웃)", description = """
            body `{ token }`. 로그아웃 API(POST /api/auth/logout)보다 **먼저** 부른다(로그아웃 뒤에는 인증이 없어 401).
            내 토큰이 아니거나 없으면 아무것도 하지 않고 200이다.
            """)
    @DeleteMapping
    public ApiResponse<Void> unregister(@Valid @RequestBody PushTokenDeleteRequest request) {
        pushTokenService.unregister(SecurityUtils.getCurrentUserId(), request.token());
        return ApiResponse.ok(null);
    }
}
