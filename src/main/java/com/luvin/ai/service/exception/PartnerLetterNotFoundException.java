package com.luvin.ai.service.exception;

/** 존재하지 않거나 본인 소유가 아닌 letterId. 다른 사용자 자원의 존재 여부를 노출하지 않는다. */
public class PartnerLetterNotFoundException extends RuntimeException {
    public PartnerLetterNotFoundException() {
        super("쪽지를 찾을 수 없습니다.");
    }
}
