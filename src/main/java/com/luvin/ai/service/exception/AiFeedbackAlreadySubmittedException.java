package com.luvin.ai.service.exception;

/** 같은 회차에 "나의 빵에게" 피드백을 두 번째로 쓰려고 할 때 (회차당 1회만 허용). */
public class AiFeedbackAlreadySubmittedException extends RuntimeException {
    public AiFeedbackAlreadySubmittedException() {
        super("이미 이 회차에 피드백을 작성했습니다.");
    }
}
