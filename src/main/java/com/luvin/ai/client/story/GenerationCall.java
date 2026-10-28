package com.luvin.ai.client.story;

import java.util.function.Consumer;

/**
 * FastAPI 비동기 생성 job 한 건의 호출 정보.
 *
 * @param idempotencyKey Spring의 논리 작업(시즌·회차 row·단계·시도)에 대응하는 키. 같은 키로 다시 접수하면 AI는 같은 job을
 *                       돌려주므로, Spring이 재시작한 뒤 같은 작업을 다시 돌려도 유료 생성을 새로 하지 않고 이어서 본다
 * @param onProgress     job 상태(queued·generating·regenerating)나 jobId가 바뀔 때마다 불린다. 진행 상태 저장용
 */
public record GenerationCall(String idempotencyKey, Consumer<GenerationProgress> onProgress) {

    public void report(GenerationProgress progress) {
        if (onProgress != null) {
            onProgress.accept(progress);
        }
    }
}
