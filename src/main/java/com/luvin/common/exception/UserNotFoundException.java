package com.luvin.common.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long memberId) {
        super("사용자를 찾을 수 없습니다. memberId=" + memberId);
    }
}
