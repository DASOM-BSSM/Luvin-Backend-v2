package com.luvin.ai.domain;

/**
 * 회차 콘텐츠 생성 상태. GENERATING이면 앱은 잠시 뒤 GET content를 다시 부른다.
 * FAILED는 다음 GET content 요청에서 다시 생성을 시도한다.
 */
public enum AiStoryContentStatus {
    GENERATING,
    READY,
    FAILED
}
