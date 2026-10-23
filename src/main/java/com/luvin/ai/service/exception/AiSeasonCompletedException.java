package com.luvin.ai.service.exception;

/** 끝난(COMPLETED) 시즌에 generation/selection/seen 등 쓰기 요청이 들어왔을 때. */
public class AiSeasonCompletedException extends RuntimeException {
    public AiSeasonCompletedException() {
        super("완료된 시즌에는 요청할 수 없습니다.");
    }
}
