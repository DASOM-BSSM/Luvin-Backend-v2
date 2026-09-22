package com.luvin.ai.service.exception;

public class AiSeasonAlreadyExistsException extends RuntimeException {
    public AiSeasonAlreadyExistsException() {
        super("이미 진행 중인 시즌이 있습니다.");
    }
}
