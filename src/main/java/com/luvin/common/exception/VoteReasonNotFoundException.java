package com.luvin.common.exception;

public class VoteReasonNotFoundException extends RuntimeException {
    public VoteReasonNotFoundException(Long episodeId) {
        super("투표 근거를 찾을 수 없습니다. episodeId=" + episodeId);
    }
}
