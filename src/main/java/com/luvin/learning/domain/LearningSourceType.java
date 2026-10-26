package com.luvin.learning.domain;

import java.util.List;

/** 학습 신호의 원본 종류. 원문은 각 원본 테이블에 있고 신호는 그 행을 가리키기만 한다. */
public enum LearningSourceType {
    DIARY,
    COMMENT,
    ROOM_ANSWER,
    BALANCE_ANSWER;

    /** 말투 학습(A)에 쓰는 글: 내가 쓴 일기·댓글·그룹 답. */
    public static final List<LearningSourceType> SPEECH = List.of(DIARY, COMMENT, ROOM_ANSWER);
    /** 반죽 성격 학습(B)에 쓰는 답: 밸런스 답·그룹 답. */
    public static final List<LearningSourceType> PERSONALITY = List.of(BALANCE_ANSWER, ROOM_ANSWER);
}
