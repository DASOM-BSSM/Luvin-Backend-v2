package com.luvin.common.exception;

public class DiaryRoomNotFoundException extends RuntimeException {
    public DiaryRoomNotFoundException(Long roomId) {
        super("공유방을 찾을 수 없습니다. roomId=" + roomId);
    }
}
