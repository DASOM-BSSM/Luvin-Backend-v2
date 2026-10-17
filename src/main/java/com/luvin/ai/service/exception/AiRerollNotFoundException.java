package com.luvin.ai.service.exception;

/** 해당 회차로 아직 한 번도 reroll을 요청한 적이 없을 때. */
public class AiRerollNotFoundException extends RuntimeException {
    public AiRerollNotFoundException(int episodeNumber) {
        super(episodeNumber + "화에 대한 reroll 요청을 찾을 수 없습니다.");
    }
}
