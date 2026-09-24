package com.luvin.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 안드로이드/iOS/웹 클라이언트가 각자 다른 OAuth client-id로 ID 토큰을 발급받으므로,
 * ID 토큰 검증(aud claim)은 clientIds에 등록된 것 중 하나만 일치하면 통과시킨다.
 * webClientId/clientSecret/redirectUri는 웹 리디렉션(authorization code) 플로우 전용이며,
 * Google 웹 클라이언트 하나에만 해당한다.
 */
@ConfigurationProperties(prefix = "app.oauth.google")
public record GoogleOAuthProperties(
        List<String> clientIds,
        String webClientId,
        String clientSecret,
        String redirectUri
) {
}
