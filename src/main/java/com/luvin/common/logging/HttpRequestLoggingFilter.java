package com.luvin.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 모든 HTTP 요청/응답을 "{METHOD} {URI} {STATUS} {DURATION}ms" 형식으로 기록한다.
 *
 * Spring Security의 필터 체인(springSecurityFilterChain)보다 바깥(앞)에서 실행되도록
 * HIGHEST_PRECEDENCE로 등록한다 — 그래야 인증 실패로 걸러진 요청(401/403)과 인증을 통과한
 * 요청이 같은 필터에서 동일한 형식으로 기록되고, finally 블록 시점에는 컨트롤러/
 * GlobalExceptionHandler까지 끝난 뒤의 최종 상태 코드를 읽을 수 있다.
 *
 * 쿼리스트링과 요청/응답 바디는 기록하지 않는다 — access token이 쿼리스트링으로 오는
 * 경로(OAuth 콜백 등)가 있어 URI 전체(getRequestURL) 대신 getRequestURI()만 쓴다.
 *
 * 비동기 요청/오류 디스패치는 OncePerRequestFilter 기본 동작(shouldNotFilterAsyncDispatch/
 * shouldNotFilterErrorDispatch가 true)에 따라 이 필터가 최초 1회만 감싸므로, 비동기 작업이
 * 컨테이너 스레드 반환 이후에도 계속되는 경우 DURATION은 실제 비동기 완료 시점이 아니라
 * 최초 디스패치가 끝난 시점까지만 측정한다 — job SSE(GET /api/ai/jobs/{jobId}/events)는 스트림이 열린
 * 시점에 200과 그때까지의 시간으로 한 번만 기록된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            // 로깅 자체가 요청 처리 흐름에 영향을 주지 않도록 별도로 감싼다(NFR-04).
            try {
                log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            } catch (RuntimeException loggingFailure) {
                // 로깅 실패가 원래 요청 처리 결과(또는 이미 전파 중인 예외)를 가리면 안 된다.
            }
        }
    }
}
