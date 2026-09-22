package com.luvin.common.exception;

public class MatchNotFoundException extends RuntimeException {
    public MatchNotFoundException(Long matchId) {
        super("매칭을 찾을 수 없습니다. matchId=" + matchId);
    }

    public MatchNotFoundException(String message) {
        super(message);
    }
}
