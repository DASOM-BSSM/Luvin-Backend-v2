package com.luvin.common.exception;

public class RebakeNotFoundException extends RuntimeException {
    public RebakeNotFoundException(Long episodeId, Long participantId) {
        super("다시 굽기 결과를 찾을 수 없습니다. episodeId=" + episodeId + ", participantId=" + participantId);
    }
}
