package com.luvin.survey.service.exception;

/** gender 등 시즌 생성에 필요한 프로필 정보가 없을 때. 기본 성별을 추측하지 않고 명시적으로 거부한다. */
public class ProfileRequiredException extends RuntimeException {
    public ProfileRequiredException(String message) {
        super(message);
    }
}
