package com.luvin.ai.client.story;

import com.luvin.ai.client.exception.AiServiceException;

/**
 * 블록 단위 생성의 이어 쓰기를 AI가 거부했다: 원본 job 없음(404 GENERATION_JOB_NOT_FOUND), 원본이 실패한 job이 아님
 * (409 RESUME_SOURCE_NOT_FAILED), 입력 변경(409 RESUME_INPUT_CHANGED), 설정·프롬프트 변경(409 RESUME_CONFIG_CHANGED).
 * 호출부는 앱에 나간 블록을 버리고 처음부터 다시 만든다.
 */
public class AiStreamResumeRejectedException extends AiServiceException {

    public AiStreamResumeRejectedException(String message, String aiErrorCode) {
        super(message, aiErrorCode, null);
    }
}
