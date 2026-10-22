package com.luvin.common.exception;

public class DiaryRoomInviteCodeNotFoundException extends RuntimeException {
    public DiaryRoomInviteCodeNotFoundException() {
        super("초대 코드가 올바르지 않습니다. 다시 확인해주세요.");
    }
}
