package com.luvin.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 애플 identityToken의 aud claim 검증에 허용할 클라이언트 ID 목록.
 * 네이티브(iOS)는 앱 번들 ID가, 웹(Sign in with Apple JS)은 Service ID가 aud로 오므로 전부 나열한다.
 * 비어있으면 애플 로그인 자체가 막힌다 (fail-closed) — {@link GoogleOAuthProperties}와 동일한 정책.
 *
 * teamId/keyId/bundleId/privateKey 4개는 identityToken 검증에는 쓰이지 않고, Apple REST
 * API(POST /auth/token, POST /auth/revoke) 호출 — 즉 회원가입/로그인 시 refresh token 발급과
 * 회원 탈퇴 시 그 토큰 해지에만 쓰인다. 비어있어도 로그인 자체는 막지 않는다(best-effort) —
 * {@link com.luvin.auth.service.AppleOAuthClient} 호출 시점에만 fail-closed로 예외를 던진다.
 */
@ConfigurationProperties(prefix = "app.oauth.apple")
public record AppleOAuthProperties(
        List<String> clientIds,
        String teamId,
        String keyId,
        String bundleId,
        String privateKey
) {
}
