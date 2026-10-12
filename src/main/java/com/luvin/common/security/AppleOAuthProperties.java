package com.luvin.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 애플 identityToken의 aud claim 검증에 허용할 클라이언트 ID 목록.
 * 네이티브(iOS)는 앱 번들 ID가, 웹(Sign in with Apple JS)은 Service ID가 aud로 오므로 전부 나열한다.
 * 비어있으면 애플 로그인 자체가 막힌다 (fail-closed) — {@link GoogleOAuthProperties}와 동일한 정책.
 */
@ConfigurationProperties(prefix = "app.oauth.apple")
public record AppleOAuthProperties(
        List<String> clientIds
) {
}
