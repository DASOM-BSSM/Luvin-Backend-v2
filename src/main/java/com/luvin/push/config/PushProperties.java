package com.luvin.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 푸시 알림 설정(app.push).
 *
 * @param client      expo(기본) | noop. noop이면 보내지 않고 로그만 남긴다(테스트·로컬)
 * @param expoUrl     Expo 푸시 전송 API(기본 https://exp.host/--/api/v2/push/send)
 * @param accessToken Expo 액세스 토큰. Expo 프로젝트에서 "Enhanced push security"를 켰을 때만 필요하다(비어 있으면 보내지 않음)
 * @param timeout     전송 요청 연결·응답 대기 시간(기본 10초)
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(String client, String expoUrl, String accessToken, Duration timeout) {

    public static final String CLIENT_EXPO = "expo";

    public PushProperties {
        if (client == null || client.isBlank()) {
            client = CLIENT_EXPO;
        }
        if (expoUrl == null || expoUrl.isBlank()) {
            expoUrl = "https://exp.host/--/api/v2/push/send";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
    }
}
