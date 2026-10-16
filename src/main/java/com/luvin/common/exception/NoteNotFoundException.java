package com.luvin.common.exception;

public class NoteNotFoundException extends RuntimeException {
    public NoteNotFoundException(Long episodeId) {
        super("쪽지를 찾을 수 없습니다. episodeId=" + episodeId);
    }
}
