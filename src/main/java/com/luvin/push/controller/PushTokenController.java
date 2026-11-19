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

@Tag(name = "푸시 토큰")
@RestController
@RequestMapping("/api/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

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
