package com.luvin.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * partnerName/partnerBreadType은 발신 빵(partnerId = characters[].characterId)의 시즌 출연진 이름("형용사 + 빵 종류")과
 * 빵 종류다. 출연진 이름이 없는 예전 시즌 쪽지는 null이다.
 */
public record PartnerLetterView(
        Long letterId,
        UUID seasonId,
        UUID partnerId,
        String partnerName,
        String partnerBreadType,
        String message,
        LocalDateTime createdAt,
        String renderMode
) {
}
