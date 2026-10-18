package com.luvin.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * partnerName/partnerBreadType은 현재 AI 캐릭터 계약(characterId/role/gender뿐)에 이름/빵타입
 * 데이터 자체가 없어 항상 null이다 — 데이터가 생기면 채울 자리를 미리 만들어 둔 것이다.
 */
public record PartnerLetterView(
        Long letterId,
        UUID seasonId,
        UUID partnerId,
        String partnerName,
        String partnerBreadType,
        String message,
        LocalDateTime createdAt
) {
}
