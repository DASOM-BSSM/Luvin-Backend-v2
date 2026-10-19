package com.luvin.common.exception;

/**
 * authorization code ↔ refresh token 교환, 또는 refresh token 해지(revoke) 호출이 실패했을 때.
 * identityToken 검증 실패({@link InvalidAppleTokenException}, 401 로그인 거부)와는 성격이 다르다 —
 * 이 예외는 항상 호출부(로그인 시 토큰 저장, 탈퇴 시 토큰 해지)에서 best-effort로 잡아서 처리하고
 * 컨트롤러까지 올리지 않는다. 그래서 GlobalExceptionHandler에 별도 HTTP 매핑이 없다.
 */
public class AppleTokenExchangeException extends RuntimeException {
    public AppleTokenExchangeException(String message) {
        super(message);
    }
}
