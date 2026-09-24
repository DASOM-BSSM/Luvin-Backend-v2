package com.luvin.ai.client;

import com.luvin.ai.client.dto.CharacterProfileDto;
import com.luvin.ai.client.dto.CreateSeasonRequestDto;
import com.luvin.ai.client.dto.SeasonResponseDto;
import com.luvin.ai.client.dto.TraitsDto;
import com.luvin.ai.config.AiClientConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AWS에 배포된 실제 AI 서비스(FastAPI)에 대한 네트워크/계약 연동을 확인하는 수동 통합 테스트.
 * DB 등 나머지 앱 구성 없이 AI 클라이언트 계층(AiClientConfig + AiServiceClient)만 컨텍스트에 올려
 * 실제 base-url(app.ai.base-url, 기본값 AI_SERVICE_BASE_URL 환경변수/.env)로 요청을 보낸다.
 *
 * AI 서비스의 보안그룹이 특정 IP만 허용하므로 CI 러너에서는 도달할 수 없다 — manual 태그로
 * 기본 ./gradlew test에서 제외한다 (./gradlew manualTest로 로컬에서만 실행).
 */
@Tag("manual")
class AiServiceClientConnectionTest {

    // ApplicationContextRunner는 SpringApplication 부트스트랩을 거치지 않아 spring-dotenv(.env)가
    // 적용되지 않으므로, 여기서는 OS 환경변수를 직접 읽어 app.ai.base-url로 넘긴다.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiClientConfig.class, AiServiceClient.class)
            .withPropertyValues("app.ai.base-url=" + System.getenv().getOrDefault(
                    "AI_SERVICE_BASE_URL", "http://54.180.127.52:8000"));

    @Test
    void createSeason_reachesRealAiService() {
        contextRunner.run(context -> {
            AiServiceClient client = context.getBean(AiServiceClient.class);

            CreateSeasonRequestDto request = CreateSeasonRequestDto.of(new CharacterProfileDto(
                    "female",
                    25,
                    "친절하고 다정한 성격",
                    new TraitsDto(50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50)
            ));

            SeasonResponseDto response = client.createSeason(
                    "connection-test-" + UUID.randomUUID(), UUID.randomUUID(), request);

            assertNotNull(response.seasonId());
            System.out.println("AI 서비스 연결 성공, seasonId=" + response.seasonId());
        });
    }
}
