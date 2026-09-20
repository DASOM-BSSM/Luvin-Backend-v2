package com.luvin.common.exception;

public class DuplicateAnswerException extends RuntimeException {

    public DuplicateAnswerException(Long questionId) {
        super("이미 응답한 질문입니다. id=" + questionId);
    }
}

