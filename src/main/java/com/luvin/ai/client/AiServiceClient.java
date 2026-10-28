package com.luvin.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luvin.ai.client.dto.AiSseEventDto;
import com.luvin.ai.client.dto.CreateSeasonRequestDto;
import com.luvin.ai.client.dto.EpisodeResponseDto;
import com.luvin.ai.client.dto.GenerationRequestDto;
import com.luvin.ai.client.dto.JobResponseDto;
import com.luvin.ai.client.dto.LetterRequestDto;
import com.luvin.ai.client.dto.LetterResponseDto;
import com.luvin.ai.client.dto.ReportResponseDto;
import com.luvin.ai.client.dto.SeasonResponseDto;
import com.luvin.ai.client.dto.SelectionRequestDto;
import com.luvin.ai.client.dto.SelectionResponseDto;
import com.luvin.ai.client.exception.AiAuthConfigException;
import com.luvin.ai.client.exception.AiBadRequestException;
import com.luvin.ai.client.exception.AiContractViolationException;
import com.luvin.ai.client.exception.AiLetterAlreadyExistsException;
import com.luvin.ai.client.exception.AiNotFoundException;
import com.luvin.ai.client.exception.AiRateLimitException;
import com.luvin.ai.client.exception.AiReportOutdatedException;
import com.luvin.ai.client.exception.AiRevisionConflictException;
import com.luvin.ai.client.exception.AiSelectionConflictException;
import com.luvin.ai.client.exception.AiServerException;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.client.exception.AiUnclassifiedConflictException;
import com.luvin.ai.client.exception.AiValidationException;
import com.luvin.ai.client.exception.AiVersionSupersededException;
import com.luvin.ai.config.AiServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * AI 서비스(FastAPI) endpoint에 대한 얇은 HTTP client.
 * 모든 요청에 X-Owner-Subject를 전달하고, 모든 POST에는 Idempotency-Key(UUID)를 전달한다 (요구사항 3.2).
 * HTTP status와 body를 도메인 예외로 변환하는 책임까지 이 계층에서 진다 (요구사항 6절).
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper aiObjectMapper;
    private final HttpClient streamHttpClient;
    private final String baseUrl;
    private final Duration readTimeout;

    public AiServiceClient(@Qualifier("aiRestClient") RestClient aiRestClient, ObjectMapper aiObjectMapper,
                           HttpClient aiStreamHttpClient, AiServiceProperties properties) {
        this.restClient = aiRestClient;
        this.aiObjectMapper = aiObjectMapper;
        this.streamHttpClient = aiStreamHttpClient;
        this.baseUrl = properties.baseUrl().endsWith("/")
                ? properties.baseUrl().substring(0, properties.baseUrl().length() - 1)
                : properties.baseUrl();
        this.readTimeout = properties.readTimeout();
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
                .headers(h -> applyHeaders(h, ownerSubject, (String) null))
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

    public JobResponseDto getJob(String ownerSubject, UUID jobId) {
        return withNetworkErrorTranslation("GET /v1/jobs/{id}", () -> restClient.get()
                .uri("/v1/jobs/{jobId}", jobId)
                .headers(h -> applyHeaders(h, ownerSubject, (String) null))
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
                .headers(h -> applyHeaders(h, ownerSubject, (String) null))
                .exchange((req, resp) -> handle(resp, "GET .../episodes/{number}", EpisodeResponseDto.class)));
    }

    public ReportResponseDto getReport(String ownerSubject, UUID seasonId) {
        return withNetworkErrorTranslation("GET .../report", () -> restClient.get()
                .uri("/v1/seasons/{seasonId}/report", seasonId)
                .headers(h -> applyHeaders(h, ownerSubject, (String) null))
                .exchange((req, resp) -> handle(resp, "GET .../report", ReportResponseDto.class)));
    }

    /** "SECRET LOVE" 쪽지 생성을 접수한다(202/job). 응답의 result_version_id는 쪽지 job에서는 항상 null이다. */
    public JobResponseDto requestLetter(String ownerSubject, UUID idempotencyKey, UUID seasonId, LetterRequestDto request) {
        return withNetworkErrorTranslation("POST .../letter", () -> restClient.post()
                .uri("/v1/seasons/{seasonId}/letter", seasonId)
                .headers(h -> applyHeaders(h, ownerSubject, idempotencyKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, resp) -> handle(resp, "POST .../letter", JobResponseDto.class)));
    }

    /** 생성된 쪽지 본문을 조회한다. 아직 생성되지 않았으면 404(LETTER_NOT_READY)로 응답한다. */
    public LetterResponseDto getLetter(String ownerSubject, UUID seasonId) {
        return withNetworkErrorTranslation("GET .../letter", () -> restClient.get()
                .uri("/v1/seasons/{seasonId}/letter", seasonId)
                .headers(h -> applyHeaders(h, ownerSubject, (String) null))
                .exchange((req, resp) -> handle(resp, "GET .../letter", LetterResponseDto.class)));
    }

    /**
     * 장면 블록 회차 생성 endpoint(AiStoryHttpClient) 전용 POST. 생성 호출은 일반 호출보다 오래 걸려 timeout이 긴
     * 별도 RestClient로 보내고, 공통 데이터 계약대로 camelCase(responseMapper)로 읽는다. 헤더 규칙과 오류 변환은
     * 다른 endpoint와 똑같이 이 클래스가 맡는다.
     */
    public <T> T post(RestClient client, ObjectMapper responseMapper, String path, String ownerSubject,
                      UUID idempotencyKey, Object request, Class<T> type) {
        return post(client, responseMapper, path, ownerSubject, idempotencyKey.toString(), request, type);
    }

    /** Idempotency-Key를 Spring의 논리 작업 키(문자열)로 보낸다. 같은 키로 다시 보내면 AI가 같은 작업을 돌려준다. */
    public <T> T post(RestClient client, ObjectMapper responseMapper, String path, String ownerSubject,
                      String idempotencyKey, Object request, Class<T> type) {
        String opName = "POST " + path;
        return withNetworkErrorTranslation(opName, () -> client.post()
                .uri(path)
                .headers(h -> applyHeaders(h, ownerSubject, idempotencyKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, resp) -> handle(resp, opName, type, responseMapper)));
    }

    /** opName은 로그용 경로(식별자 없는 템플릿)다. */
    public <T> T get(RestClient client, ObjectMapper responseMapper, String opPath, String uri, String ownerSubject,
                     Class<T> type) {
        String opName = "GET " + opPath;
        return withNetworkErrorTranslation(opName, () -> client.get()
                .uri(uri)
                .headers(h -> applyHeaders(h, ownerSubject, (String) null))
                .exchange((req, resp) -> handle(resp, opName, type, responseMapper)));
    }

    /**
     * job 진행 이벤트(SSE)를 구독해 이벤트가 올 때마다 onEvent로 넘긴다. onEvent가 false를 돌려주면 구독을 끝낸다.
     * lastEventId가 있으면 Last-Event-ID로 보내 그 다음 이벤트부터 받는다.
     *
     * read-timeout은 응답 헤더를 받을 때까지만 적용하고 본문은 시간 제한 없이 읽는다 — 다른 스레드에서 끊으려면
     * onOpen으로 받은 본문 스트림을 닫는다(그러면 읽던 쪽은 IOException으로 빠져나온다).
     * 오류 응답은 다른 endpoint와 같은 예외로 바꾸고, 연결/읽기 실패는 AiServerException으로 던진다.
     */
    public void streamJobEvents(String ownerSubject, UUID jobId, String lastEventId,
                                Consumer<Closeable> onOpen, Predicate<AiSseEventDto> onEvent) {
        String opName = "GET /v1/jobs/{id}/events";
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/jobs/" + jobId + "/events"))
                .timeout(readTimeout)
                .header("X-Owner-Subject", ownerSubject)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET();
        if (lastEventId != null && !lastEventId.isBlank()) {
            request.header("Last-Event-ID", lastEventId);
        }

        HttpResponse<InputStream> response;
        try {
            response = streamHttpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            log.warn("AI service network failure: op={}, cause={}", opName, e.getMessage());
            throw new AiServerException(opName + " 네트워크 오류/timeout", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServerException(opName + " 연결 중 중단됨", e);
        }

        try (InputStream body = response.body()) {
            if (response.statusCode() / 100 != 2) {
                throw toException(response.statusCode(), opName, new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("");
            if (!contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)) {
                throw new AiContractViolationException(opName + " 응답이 text/event-stream이 아닙니다: " + contentType, null);
            }
            onOpen.accept(body);
            AiSseEventReader.read(new InputStreamReader(body, StandardCharsets.UTF_8), onEvent);
        } catch (IOException e) {
            throw new AiServerException(opName + " 스트림 읽기 중 IO 오류", e);
        }
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
        applyHeaders(headers, ownerSubject, idempotencyKey == null ? null : idempotencyKey.toString());
    }

    private void applyHeaders(HttpHeaders headers, String ownerSubject, String idempotencyKey) {
        headers.set("X-Owner-Subject", ownerSubject);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
    }

    private <T> T handle(org.springframework.http.client.ClientHttpResponse response, String opName, Class<T> type) {
        return handle(response, opName, type, aiObjectMapper);
    }

    private <T> T handle(org.springframework.http.client.ClientHttpResponse response, String opName, Class<T> type,
                         ObjectMapper mapper) {
        try {
            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                return readBody(response, type, mapper);
            }
            String rawBody = readRawBody(response);
            throw toException(status.value(), opName, rawBody);
        } catch (java.io.IOException e) {
            throw new AiServerException(opName + " 응답 처리 중 IO 오류", e);
        }
    }

    private <T> T readBody(org.springframework.http.client.ClientHttpResponse response, Class<T> type,
                           ObjectMapper mapper) throws java.io.IOException {
        try (var body = response.getBody()) {
            return mapper.readValue(body, type);
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
            case 400 -> new AiBadRequestException(message, parsed.errorCode(), rawBody);
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
            // errorCode를 특정할 수 없어도 HTTP status가 이미 409(충돌)임은 확실하다 — 일반
            // AiServiceException(500계열로 처리됨)이 아니라 전용 타입으로 구분해 재시도 가능하게 한다.
            return new AiUnclassifiedConflictException(message, rawBody);
        }
        return switch (errorCode) {
            case "REVISION_CONFLICT" -> new AiRevisionConflictException(message, errorCode, rawBody);
            case "VERSION_SUPERSEDED" -> new AiVersionSupersededException(message, rawBody);
            // 오류가 아니라 "이미 정상 생성됨" — 호출부가 GET /letter로 받아와 성공 처리하도록 구분한다.
            case "LETTER_ALREADY_EXISTS" -> new AiLetterAlreadyExistsException(message, rawBody);
            // 2025 리포트 스키마 변경(PR #5) 배포 전에 생성된 구 스키마 리포트 — 재생성 수단이
            // 없어 그대로 앱에 code로 노출한다.
            case "REPORT_OUTDATED" -> new AiReportOutdatedException(message, rawBody);
            // REPORT_REQUIRED/JOB_IN_PROGRESS/LETTER_ATTEMPTS_EXHAUSTED/IDEMPOTENCY_CONFLICT 등
            // 쪽지 전용 코드는 전부 getAiErrorCode()로 코드를 보존한 채 아래 기본 분기로 흘려보낸다 —
            // 전부 "이번엔 접수 못 함, 다음 스케줄러 tick에 재시도"로 동일하게 처리되기 때문이다.
            default -> new AiSelectionConflictException(message, errorCode, rawBody);
        };
    }
}
