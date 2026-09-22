package com.luvin.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.ai.client.dto.CreateSeasonRequestDto;
import com.luvin.ai.client.dto.EpisodeResponseDto;
import com.luvin.ai.client.dto.GenerationRequestDto;
import com.luvin.ai.client.dto.JobResponseDto;
import com.luvin.ai.client.dto.ReportResponseDto;
import com.luvin.ai.client.dto.RerollRequestDto;
import com.luvin.ai.client.dto.SeasonResponseDto;
import com.luvin.ai.client.dto.SelectionRequestDto;
import com.luvin.ai.client.dto.SelectionResponseDto;
import com.luvin.ai.client.exception.AiAuthConfigException;
import com.luvin.ai.client.exception.AiContractViolationException;
import com.luvin.ai.client.exception.AiNotFoundException;
import com.luvin.ai.client.exception.AiRateLimitException;
import com.luvin.ai.client.exception.AiRerollLockedException;
import com.luvin.ai.client.exception.AiRevisionConflictException;
import com.luvin.ai.client.exception.AiSelectionConflictException;
import com.luvin.ai.client.exception.AiServerException;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.client.exception.AiValidationException;
import com.luvin.ai.client.exception.AiVersionSupersededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * AI 서비스(FastAPI) 8개 endpoint에 대한 얇은 HTTP client.
 * 모든 요청에 X-Owner-Subject를 전달하고, 모든 POST에는 Idempotency-Key(UUID)를 전달한다 (요구사항 3.2).
 * HTTP status와 body를 도메인 예외로 변환하는 책임까지 이 계층에서 진다 (요구사항 6절).
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper aiObjectMapper;

    public AiServiceClient(RestClient aiRestClient, ObjectMapper aiObjectMapper) {
        this.restClient = aiRestClient;
        this.aiObjectMapper = aiObjectMapper;
    }

    public SeasonResponseDto createSeason(String ownerSubject, UUID idempotencyKey, CreateSeasonRequestDto request) {
        return withNetworkErrorTranslation("POST /v1/seasons", () -> restClient.post()
                .uri("/v1/seasons")
                .headers(h -> applyHeaders(h, ownerSubject, idempotencyKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, resp) -> handle(resp, "POST /v1/seasons", SeasonResponseDto.class)));
    }

    public SeasonResponseDto getSeason(String ownerSubject, UUID seasonId) {
        return withNetworkErrorTranslation("GET /v1/seasons/{id}", () -> restClient.get()
                .uri("/v1/seasons/{seasonId}", seasonId)
                .headers(h -> applyHeaders(h, ownerSubject, null))
                .exchange((req, resp) -> handle(resp, "GET /v1/seasons/{id}", SeasonResponseDto.class)));
    }

    public JobResponseDto requestGeneration(String ownerSubject, UUID idempotencyKey,
                                             UUID seasonId, int episodeNumber, GenerationRequestDto request) {
        return withNetworkErrorTranslation("POST .../generations", () -> restClient.post()
                .uri("/v1/seasons/{seasonId}/episodes/{number}/generations", seasonId, episodeNumber)
                .headers(h -> applyHeaders(h, ownerSubject, idempotencyKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, resp) -> handle(resp, "POST .../generations", JobResponseDto.class)));
    }

    public SelectionResponseDto submitSelection(String ownerSubject, UUID idempotencyKey,
                                                 UUID seasonId, int episodeNumber, SelectionRequestDto request) {
        return withNetworkErrorTranslation("POST .../selection", () -> restClient.post()
                .uri("/v1/seasons/{seasonId}/episodes/{number}/selection", seasonId, episodeNumber)
                .headers(h -> applyHeaders(h, ownerSubject, idempotencyKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, resp) -> handle(resp, "POST .../selection", SelectionResponseDto.class)));
    }

    public JobResponseDto requestReroll(String ownerSubject, UUID idempotencyKey,
                                         UUID seasonId, int episodeNumber, RerollRequestDto request) {
        return withNetworkErrorTranslation("POST .../rerolls", () -> restClient.post()
                .uri("/v1/seasons/{seasonId}/episodes/{number}/rerolls", seasonId, episodeNumber)
                .headers(h -> applyHeaders(h, ownerSubject, idempotencyKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, resp) -> handle(resp, "POST .../rerolls", JobResponseDto.class)));
    }

    public JobResponseDto getJob(String ownerSubject, UUID jobId) {
        return withNetworkErrorTranslation("GET /v1/jobs/{id}", () -> restClient.get()
                .uri("/v1/jobs/{jobId}", jobId)
                .headers(h -> applyHeaders(h, ownerSubject, null))
                .exchange((req, resp) -> handle(resp, "GET /v1/jobs/{id}", JobResponseDto.class)));
    }

    public EpisodeResponseDto getEpisode(String ownerSubject, UUID seasonId, int episodeNumber,
                                          UUID versionId, Integer afterSequence, Integer limit) {
        return withNetworkErrorTranslation("GET .../episodes/{number}", () -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v1/seasons/{seasonId}/episodes/{number}");
                    if (versionId != null) {
                        uriBuilder.queryParam("version_id", versionId);
                    }
                    if (afterSequence != null) {
                        uriBuilder.queryParam("after_sequence", afterSequence);
                    }
                    if (limit != null) {
                        uriBuilder.queryParam("limit", limit);
                    }
                    return uriBuilder.build(seasonId, episodeNumber);
                })
                .headers(h -> applyHeaders(h, ownerSubject, null))
                .exchange((req, resp) -> handle(resp, "GET .../episodes/{number}", EpisodeResponseDto.class)));
    }

    public ReportResponseDto getReport(String ownerSubject, UUID seasonId) {
        return withNetworkErrorTranslation("GET .../report", () -> restClient.get()
                .uri("/v1/seasons/{seasonId}/report", seasonId)
                .headers(h -> applyHeaders(h, ownerSubject, null))
                .exchange((req, resp) -> handle(resp, "GET .../report", ReportResponseDto.class)));
    }

    /**
     * 연결 실패/timeout(ResourceAccessException)은 job 실패가 아니라 재시도 가능한 서버 오류로 취급한다
     * (요구사항 5.3, 6절).
     */
    private <T> T withNetworkErrorTranslation(String opName, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException networkFailure) {
            log.warn("AI service network failure: op={}, cause={}", opName, networkFailure.getMessage());
            throw new AiServerException(opName + " 네트워크 오류/timeout", networkFailure);
        }
    }

    private void applyHeaders(HttpHeaders headers, String ownerSubject, UUID idempotencyKey) {
        headers.set("X-Owner-Subject", ownerSubject);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey.toString());
        }
    }

    private <T> T handle(org.springframework.http.client.ClientHttpResponse response, String opName, Class<T> type) {
        try {
            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                return readBody(response, type);
            }
            String rawBody = readRawBody(response);
            throw toException(status.value(), opName, rawBody);
        } catch (java.io.IOException e) {
            throw new AiServerException(opName + " 응답 처리 중 IO 오류", e);
        }
    }

    private <T> T readBody(org.springframework.http.client.ClientHttpResponse response, Class<T> type)
            throws java.io.IOException {
        try (var body = response.getBody()) {
            return aiObjectMapper.readValue(body, type);
        }
    }

    private String readRawBody(org.springframework.http.client.ClientHttpResponse response) {
        try (var body = response.getBody()) {
            return new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private AiServiceException toException(int statusCode, String opName, String rawBody) {
        AiErrorBodyParser.ParsedError parsed = AiErrorBodyParser.parse(aiObjectMapper, rawBody);
        String message = "[" + opName + "] AI 서비스 오류 (" + statusCode + "): " + parsed.message();

        // 원문 body와 provider 상세를 애플리케이션 로그에는 남기지 않고, 상관관계 추적 가능한 요약만 기록한다.
        log.warn("AI service call failed: op={}, status={}, errorCode={}", opName, statusCode, parsed.errorCode());

        return switch (statusCode) {
            case 401 -> new AiAuthConfigException(message, rawBody);
            case 404 -> new AiNotFoundException(message, rawBody);
            case 409 -> mapConflict(message, parsed.errorCode(), rawBody);
            case 422 -> new AiValidationException(message, rawBody);
            case 429 -> new AiRateLimitException(message, rawBody);
            default -> {
                if (statusCode >= 500) {
                    yield new AiServerException(message, rawBody);
                }
                yield new AiServiceException(message, parsed.errorCode(), rawBody);
            }
        };
    }

    private AiServiceException mapConflict(String message, String errorCode, String rawBody) {
        if (errorCode == null) {
            return new AiServiceException(message, null, rawBody);
        }
        return switch (errorCode) {
            case "REVISION_CONFLICT" -> new AiRevisionConflictException(message, errorCode, rawBody);
            case "REROLL_LOCKED" -> new AiRerollLockedException(message, rawBody);
            case "VERSION_SUPERSEDED" -> new AiVersionSupersededException(message, rawBody);
            default -> new AiSelectionConflictException(message, errorCode, rawBody);
        };
    }
}
