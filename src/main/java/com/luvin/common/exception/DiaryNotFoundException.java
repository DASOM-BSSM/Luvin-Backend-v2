package com.luvin.common.exception;

public class DiaryNotFoundException extends RuntimeException {
    public DiaryNotFoundException(Long diaryId) {
        super("일기를 찾을 수 없습니다. diaryId=" + diaryId);
    }
}
