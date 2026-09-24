package com.luvin.ai.controller;

import com.luvin.common.security.AuthenticatedUser;
import com.luvin.common.security.JwtProperties;
import com.luvin.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECS에 배포된 Spring 백엔드가 Service Connect(http://luvin-ai:8000)를 통해 AI 서비스에
 * 실제로 도달하는지 외부에서 검증하는 수동 스모크 테스트. DB/전체 컨텍스트 없이 JwtTokenProvider만
 * 올려 실제 배포본과 같은 서명 키로 토큰을 만들고, 배포된 공인 IP로 직접 HTTP 요청을 보낸다.
 * JWT_SECRET_FOR_TEST 환경변수(배포된 시크릿 값)가 필요하다.
 *
 * 배포된 ALB에 실제로 의존하므로 manual 태그로 기본 ./gradlew test에서 제외한다
 * (./gradlew manualTest로 로컬에서만 실행).
 */
@Tag("manual")
class DeployedBackendServiceConnectSmokeTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtTokenProviderTestConfig {
        @Bean
        JwtTokenProvider jwtTokenProvider(JwtProperties props) {
            return new JwtTokenProvider(props);
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtTokenProviderTestConfig.class)
            .withPropertyValues(
                    "app.jwt.secret=" + System.getenv("JWT_SECRET_FOR_TEST"),
                    "app.jwt.access-token-expiration-seconds=600");

    @Test
    void deployedBackendReachesAiServiceThroughServiceConnect() throws Exception {
        // Fargate task마다 공인 IP가 바뀌므로 하드코딩하지 않는다. 실행 전 반드시
        // BACKEND_URL_FOR_TEST=http://<현재 task 공인 IP>:8080 로 지정해야 한다.
        String backendUrl = System.getenv("BACKEND_URL_FOR_TEST");

        contextRunner.run(context -> {
            JwtTokenProvider provider = context.getBean(JwtTokenProvider.class);
            String token = provider.createAccessToken(
                    new AuthenticatedUser(999999999L, "smoke@test.local", "smoke"));

            String body = "{\"representative\":{\"gender\":\"female\",\"adultAge\":25," +
                    "\"personality\":\"친절하고 다정한 성격\",\"traits\":{" +
                    "\"affectionExpression\":50,\"relationshipAnxiety\":50,\"relationshipAvoidance\":50," +
                    "\"emotionalAttunement\":50,\"relationshipInitiative\":50,\"practicalPriority\":50," +
                    "\"reassuranceNeed\":50,\"jealousyReactivity\":50,\"relationshipEnergyDependence\":50," +
                    "\"emotionalSuppression\":50,\"conflictConfrontation\":50,\"relationshipPace\":50," +
                    "\"interestExpressionFrequency\":50}}}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/ai/seasons"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("status=" + response.statusCode());
            System.out.println("body=" + response.body());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"seasonId\""));
        });
    }
}
