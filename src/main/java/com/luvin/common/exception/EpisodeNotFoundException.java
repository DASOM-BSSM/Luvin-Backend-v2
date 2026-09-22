package com.luvin.common.exception;

public class EpisodeNotFoundException extends RuntimeException {
    public EpisodeNotFoundException(Long episodeId) {
        super("에피소드를 찾을 수 없습니다. episodeId=" + episodeId);
    }
}
