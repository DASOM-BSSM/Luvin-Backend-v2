package com.luvin.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * fullName은 애플이 최초 1회 인증 때만 클라이언트에 내려주므로 그때만 값이 있다
 * (재로그인 시에는 null — 이미 저장된 사용자 정보를 덮어쓰지 않는다).
 *
 * authorizationCode는 선택이다 — 클라이언트(예: Flutter sign_in_with_apple)가 로그인 시
 * identityToken과 함께 받는 값으로, 서버가 이걸로 Apple refresh token을 발급받아 저장해 두면
 * 나중에 회원 탈퇴 시점에 사용자에게 재인증을 요구하지 않고 바로 토큰을 해지할 수 있다. 5분
 * 안에 한 번만 쓸 수 있어 누락되거나 교환에 실패해도 로그인 자체는 막지 않는다(best-effort).
 */
public record AppleLoginRequest(
        @NotBlank(message = "애플 identityToken은 필수입니다.")
        String identityToken,
        String fullName,
        String authorizationCode
) {
}
