package com.luvin.common.exception;

/** 방장이 나가거나 자기 자신을 강퇴하려 할 때. 방에 주인이 없어지므로 막는다. */
public class DiaryRoomOwnerCannotLeaveException extends RuntimeException {
    public DiaryRoomOwnerCannotLeaveException() {
        super("방장은 공유방을 나갈 수 없습니다. 방을 없애려면 공유방을 삭제해주세요.");
    }
}
