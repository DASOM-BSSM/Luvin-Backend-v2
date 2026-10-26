package com.luvin.diary.domain;

/** 그룹 질문 생성 상태. 응답에는 소문자(generating | ready | failed)로 나간다. */
public enum RoomQuestionStatus {
    GENERATING,
    READY,
    FAILED
}
