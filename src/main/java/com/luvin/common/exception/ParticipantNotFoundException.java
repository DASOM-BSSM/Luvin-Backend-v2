package com.luvin.common.exception;

public class ParticipantNotFoundException extends RuntimeException {
    public ParticipantNotFoundException(Long participantId) {
        super("참가자를 찾을 수 없습니다. participantId=" + participantId);
    }
}
