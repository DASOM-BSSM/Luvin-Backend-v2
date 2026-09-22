package com.luvin.ai.dto;

import java.util.UUID;

/**
 * 컨트롤러 계층 입력 형태일 뿐이다. success/partnerId를 그대로 신뢰하지 않고,
 * 반드시 Spring이 서버 측에서 이미 검증/판정한 미니게임 결과를 넘겨야 한다 — 이 값을 클라이언트의
 * 자기 신고(self-report)로 그대로 받아 쓰면 안 된다 (요구사항 5.4, 1). 실제 배선 시 미니게임 도메인의
 * 판정 결과 엔티티를 조회해서 이 값을 채우는 서비스 계층을 앞단에 둬야 한다.
 */
public record Episode3ResultHttpRequest(
        boolean success,
        UUID partnerId
) {
}
