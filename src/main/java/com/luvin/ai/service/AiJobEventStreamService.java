package com.luvin.ai.service;

import com.luvin.ai.client.AiServiceClient;
import com.luvin.ai.client.dto.AiSseEventDto;
import com.luvin.ai.client.exception.AiBadRequestException;
import com.luvin.ai.client.exception.AiNotFoundException;
import com.luvin.ai.client.exception.AiRateLimitException;
import com.luvin.ai.client.exception.AiServerException;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.config.AiServiceProperties;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
import com.luvin.ai.repository.PartnerLetterRepository;
import com.luvin.ai.security.OwnerSubjectProvider;
import com.luvin.ai.service.exception.AiJobNotFoundException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 서비스의 job SSE(GET /v1/jobs/{job_id}/events)를 앱으로 그대로 중계한다.
 *
 * 이벤트 이름·id·data는 손대지 않고 넘긴다. delta의 text는 검증 전 JSON 조각이라 앱이 segment별로 모았다가
 * segment_end에서 처리하고, segment_error면 버린다 — Spring은 내용을 해석하지 않는다.
 * completed/error를 받으면 그 job 상태를 먼저 DB에 반영한 뒤(AiJobPollingScheduler.pollJobNow) 앱에 넘기고
 * 연결을 닫는다 — 앱이 completed를 받자마자 조회 API를 불러도 결과가 나오게 하기 위함이다.
 * 앱이 끊고 나가도 AI job은 취소하지 않는다(구독만 닫는다). 그때는 평소 polling이 결과를 반영한다.
 *
 * AI 쪽 구독이 실패하거나 completed/error 없이 끊기면 stream_error 이벤트({"error_code": ...})를 보내고 닫는다.
 * 앱은 받은 마지막 id로 다시 붙거나 기존 polling으로 돌아가면 된다.
 *
 * 스트림 하나당 virtual thread 2개(AI 본문 읽기, heartbeat)를 쓴다.
 */
@Service
public class AiJobEventStreamService {

    private static final Logger log = LoggerFactory.getLogger(AiJobEventStreamService.class);

    private static final Set<String> TERMINAL_EVENTS = Set.of("completed", "error");

    private final AiServiceClient aiServiceClient;
    private final AiJobPollingScheduler pollingScheduler;
    private final OwnerSubjectProvider ownerSubjectProvider;
    private final AiEpisodeProgressRepository episodeProgressRepository;
    private final PartnerLetterRepository partnerLetterRepository;
    private final Duration maxDuration;
    private final Duration heartbeatInterval;
    private final Semaphore slots;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<JobStream> openStreams = ConcurrentHashMap.newKeySet();

    public AiJobEventStreamService(AiServiceClient aiServiceClient,
                                   AiJobPollingScheduler pollingScheduler,
                                   OwnerSubjectProvider ownerSubjectProvider,
                                   AiEpisodeProgressRepository episodeProgressRepository,
                                   PartnerLetterRepository partnerLetterRepository,
                                   AiServiceProperties properties) {
        this.aiServiceClient = aiServiceClient;
        this.pollingScheduler = pollingScheduler;
        this.ownerSubjectProvider = ownerSubjectProvider;
        this.episodeProgressRepository = episodeProgressRepository;
        this.partnerLetterRepository = partnerLetterRepository;
        this.maxDuration = properties.stream().maxDuration();
        this.heartbeatInterval = properties.stream().heartbeatInterval();
        this.slots = new Semaphore(properties.stream().maxConcurrent());
    }

    public SseEmitter open(Long memberId, UUID jobId, String lastEventId) {
        if (!isOwnedBy(memberId, jobId)) {
            throw new AiJobNotFoundException(jobId);
        }
        String ownerSubject = ownerSubjectProvider.resolve(memberId);
        if (!slots.tryAcquire()) {
            throw new AiRateLimitException("job SSE 동시 연결 수 한도를 넘었습니다.", null);
        }

        SseEmitter emitter = new SseEmitter(maxDuration.toMillis());
        JobStream stream = new JobStream(emitter);
        openStreams.add(stream);
        emitter.onCompletion(stream::close);
        emitter.onTimeout(stream::close);
        emitter.onError(e -> stream.close());

        try {
            executor.execute(() -> stream.relay(ownerSubject, jobId, lastEventId));
            executor.execute(stream::heartbeat);
        } catch (RuntimeException e) {
            stream.close();
            throw e;
        }
        return emitter;
    }

    /** generation(회차 진행), 쪽지 중 내 것으로 저장된 job인지 본다. */
    private boolean isOwnedBy(Long memberId, UUID jobId) {
        return episodeProgressRepository.existsByJobIdAndSeason_MemberId(jobId, memberId)
                || partnerLetterRepository.existsByJobIdAndMemberId(jobId, memberId);
    }

    @PreDestroy
    void shutdown() {
        openStreams.forEach(JobStream::close);
        executor.shutdownNow();
    }

    private final class JobStream {

        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<Closeable> upstream = new AtomicReference<>();
        private volatile Thread heartbeatThread;
        private volatile boolean terminalSeen;

        private JobStream(SseEmitter emitter) {
            this.emitter = emitter;
        }

        void relay(String ownerSubject, UUID jobId, String lastEventId) {
            try {
                aiServiceClient.streamJobEvents(ownerSubject, jobId, lastEventId, this::attachUpstream,
                        event -> forward(jobId, event));
                if (!closed.get() && !terminalSeen) {
                    sendStreamError("STREAM_INTERRUPTED");
                }
            } catch (AiServiceException e) {
                // 앱이 먼저 떠나서 우리가 AI 스트림을 닫은 경우에도 여기로 온다 — 그때는 보낼 곳이 없다.
                if (!closed.get()) {
                    sendStreamError(toStreamErrorCode(e, jobId, lastEventId));
                }
            } catch (RuntimeException e) {
                if (!closed.get()) {
                    log.error("AI job SSE relay failed unexpectedly: jobId={}", jobId, e);
                    sendStreamError("INTERNAL_ERROR");
                }
            } finally {
                boolean wasOpen = !closed.get();
                close();
                if (wasOpen) {
                    emitter.complete();
                }
            }
        }

        /**
         * 앱이 다음에 할 일이 갈리도록 나눈다. 잘못된 Last-Event-ID(400)는 같은 id로 다시 붙어도 계속 실패하므로
         * 따로 알리고, 일시 장애(5xx/네트워크/429)와 달리 401·403·계약 위반은 Spring 설정 문제라 error로 남긴다.
         * HTTP status는 AiServiceClient가 이미 warn으로 기록한다.
         */
        private String toStreamErrorCode(AiServiceException e, UUID jobId, String lastEventId) {
            String cause = e.getClass().getSimpleName();
            if (e instanceof AiNotFoundException) {
                log.warn("AI job SSE relay failed: jobId={}, cause={}", jobId, cause);
                return "JOB_NOT_FOUND";
            }
            if (e instanceof AiBadRequestException && lastEventId != null && !lastEventId.isBlank()) {
                log.warn("AI job SSE rejected Last-Event-ID: jobId={}, cause={}", jobId, cause);
                return "INVALID_LAST_EVENT_ID";
            }
            if (e instanceof AiServerException || e instanceof AiRateLimitException) {
                log.warn("AI job SSE relay failed: jobId={}, cause={}", jobId, cause);
            } else {
                log.error("AI job SSE relay failed (설정/계약 확인 필요): jobId={}, cause={}", jobId, cause);
            }
            return "AI_UNAVAILABLE";
        }

        void heartbeat() {
            heartbeatThread = Thread.currentThread();
            try {
                while (!closed.get()) {
                    Thread.sleep(heartbeatInterval);
                    if (closed.get() || !send(SseEmitter.event().comment("keep-alive"))) {
                        return;
                    }
                }
            } catch (InterruptedException e) {
                // close()가 깨운 것 — 그대로 끝낸다.
            }
        }

        private void attachUpstream(Closeable body) {
            upstream.set(body);
            if (closed.get()) {
                closeQuietly(body);
            }
        }

        private boolean forward(UUID jobId, AiSseEventDto event) {
            if (closed.get()) {
                return false;
            }
            boolean terminal = TERMINAL_EVENTS.contains(event.event());
            if (terminal) {
                syncJobState(jobId);
            }
            SseEmitter.SseEventBuilder builder = SseEmitter.event();
            if (event.id() != null) {
                builder.id(event.id());
            }
            builder.name(event.event());
            // SseEmitter는 data 안의 줄바꿈을 나눠주지 않으므로 줄마다 data: 필드를 따로 쓴다.
            for (String line : event.data().split("\n", -1)) {
                builder.data(line);
            }
            if (!send(builder)) {
                return false;
            }
            if (terminal) {
                terminalSeen = true;
                return false;
            }
            return true;
        }

        /** 실패해도 앱에는 그대로 completed/error를 넘긴다 — 결과는 다음 polling 차례에 반영된다. */
        private void syncJobState(UUID jobId) {
            try {
                pollingScheduler.pollJobNow(jobId);
            } catch (RuntimeException e) {
                log.warn("AI job state sync on terminal SSE event failed, polling will catch up: jobId={}, cause={}",
                        jobId, e.getClass().getSimpleName());
            }
        }

        private void sendStreamError(String errorCode) {
            send(SseEmitter.event().name("stream_error").data("{\"error_code\":\"" + errorCode + "\"}"));
        }

        /** 앱이 연결을 끊었으면(쓰기 실패) 스트림을 정리하고 false를 돌려준다. */
        private boolean send(SseEmitter.SseEventBuilder event) {
            try {
                emitter.send(event);
                return true;
            } catch (IOException | IllegalStateException e) {
                close();
                return false;
            }
        }

        void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            openStreams.remove(this);
            slots.release();
            Closeable body = upstream.get();
            if (body != null) {
                closeQuietly(body);
            }
            Thread heartbeat = heartbeatThread;
            if (heartbeat != null) {
                heartbeat.interrupt();
            }
        }

        private void closeQuietly(Closeable body) {
            try {
                body.close();
            } catch (IOException e) {
                // 이미 끊긴 연결이면 닫다가 실패해도 할 일이 없다.
            }
        }
    }
}
