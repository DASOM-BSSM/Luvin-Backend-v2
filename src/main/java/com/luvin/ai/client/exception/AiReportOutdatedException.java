package com.luvin.ai.client.exception;

/**
 * 409 REPORT_OUTDATED. 2025 리포트 스키마 변경(PR #5) 배포 "전"에 생성된 구 스키마 리포트를
 * 조회했을 때 AI 서비스가 돌려준다. Spring이 자동으로 재생성할 방법이 없다(리포트는 5화 완료
 * 시점에 1회 확정되는 값이라 "다시 만들기" API가 없음) — 그대로 앱에 code로 노출해, 앱이
 * "새 시즌을 시작해야 최신 형식의 리포트를 볼 수 있다"는 안내/재생성 경로를 보여주도록 한다.
 */
public class AiReportOutdatedException extends AiServiceException {
    public AiReportOutdatedException(String message, String rawBody) {
        super(message, "REPORT_OUTDATED", rawBody);
    }
}
