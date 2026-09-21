package com.luvin.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "app.oauth.google")
public record GoogleOAuthProperties(String clientId) {
}
