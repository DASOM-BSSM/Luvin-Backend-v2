package com.luvin.ai.service.exception;

/**
 * 5화 완료 이전에 report를 조회하려 할 때 (요구사항 5.7).
 */
public class AiReportNotReadyException extends RuntimeException {
    public AiReportNotReadyException() {
        super("아직 리포트를 조회할 수 없습니다. 5화가 완료되지 않았습니다.");
    }
}
