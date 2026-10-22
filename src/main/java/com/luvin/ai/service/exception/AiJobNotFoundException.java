package com.luvin.ai.service.exception;

import java.util.UUID;

/** 내 generation/reroll/쪽지 job이 아닐 때. 다른 사용자의 job인지 여부도 드러내지 않도록 없는 job과 똑같이 404로 처리한다. */
public class AiJobNotFoundException extends RuntimeException {
    public AiJobNotFoundException(UUID jobId) {
        super("job을 찾을 수 없습니다: " + jobId);
    }
}
