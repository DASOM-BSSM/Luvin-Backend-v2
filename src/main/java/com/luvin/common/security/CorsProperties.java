package com.luvin.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 브라우저(웹 프론트)에서 직접 호출할 때만 필요 — 앱(RN/Expo 네이티브)은 CORS 제약을 받지
 * 않아 지금까지 설정 없이도 문제가 없었다. 비어있으면 어떤 origin도 허용하지 않는다(fail-closed).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
