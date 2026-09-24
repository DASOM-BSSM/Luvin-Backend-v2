package com.luvin.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.LuvinBackendV2Application;
import com.luvin.ai.dto.AiCharacterProfileRequest;
import com.luvin.ai.dto.AiTraitsRequest;
import com.luvin.ai.dto.CreateSeasonHttpRequest;
import com.luvin.common.security.AuthenticatedUser;
import com.luvin.common.security.JwtTokenProvider;
import com.luvin.user.domain.User;
import com.luvin.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 모바일 앱 -> Spring -> (JWT 인증 + DB) -> AI 서비스 전체 경로를 실제 인프라(RDS, AI 서버)로 검증하는 E2E 테스트.
 * 구글 로그인 대신 JwtTokenProvider로 직접 발급한 토큰을 사용해 AiSeasonController 앞단 인증만 우회한다.
 *
 * AI 서비스의 보안그룹이 특정 IP만 허용하므로 CI 러너에서는 도달할 수 없다 — manual 태그로
 * 기본 ./gradlew test에서 제외한다 (./gradlew manualTest로 로컬에서만 실행).
 */
@Tag("manual")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = LuvinBackendV2Application.class)
@AutoConfigureTestRestTemplate
class AiSeasonControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long createdUserId;

    @AfterEach
    void cleanup() {
        if (createdUserId != null) {
            userRepository.deleteById(createdUserId);
        }
    }

    @Test
    void createSeason_throughController_reachesRealAiService() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .googleId("e2e-test-" + suffix)
                .name("E2E Test")
                .email("e2e-test-" + suffix + "@example.com")
                .nickname("E2E")
                .build());
        createdUserId = user.getId();

        String token = jwtTokenProvider.createAccessToken(
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getNickname()));

        CreateSeasonHttpRequest body = new CreateSeasonHttpRequest(new AiCharacterProfileRequest(
                "female",
                new AiTraitsRequest(50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<CreateSeasonHttpRequest> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/ai/seasons",
                org.springframework.http.HttpMethod.POST,
                request,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode root = new ObjectMapper().readTree(response.getBody());
        assertTrue(root.path("success").asBoolean());
        assertNotNull(root.path("data").path("seasonId").textValue());

        System.out.println("E2E season created: " + response.getBody());
    }
}
