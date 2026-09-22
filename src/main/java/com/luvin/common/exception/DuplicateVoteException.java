package com.luvin.common.exception;

public class DuplicateVoteException extends RuntimeException {
    public DuplicateVoteException(Long episodeId) {
        super("이미 투표한 에피소드입니다. episodeId=" + episodeId);
    }
}
