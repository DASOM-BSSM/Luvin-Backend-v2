package com.luvin.common.exception;

public class InsufficientTokenException extends RuntimeException {
    public InsufficientTokenException(int required, int available) {
        super("보유한 토큰이 부족합니다. 필요: " + required + ", 보유: " + available);
    }
}