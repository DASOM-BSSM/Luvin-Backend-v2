package com.luvin.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * fullName은 애플이 최초 1회 인증 때만 클라이언트에 내려주므로 그때만 값이 있다
 * (재로그인 시에는 null — 이미 저장된 사용자 정보를 덮어쓰지 않는다).
 */
public record AppleLoginRequest(
        @NotBlank(message = "애플 identityToken은 필수입니다.")
        String identityToken,
        String fullName
) {
}
