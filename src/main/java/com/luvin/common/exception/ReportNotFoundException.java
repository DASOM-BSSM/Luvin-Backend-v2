package com.luvin.common.exception;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(Long memberId) {
        super("아직 생성된 리포트가 없습니다. memberId=" + memberId);
    }
}
