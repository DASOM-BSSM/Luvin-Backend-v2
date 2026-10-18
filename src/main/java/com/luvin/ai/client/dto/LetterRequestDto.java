package com.luvin.ai.client.dto;

/**
 * partnerBreadType/representativeDoughType/partnerSpeechStyle은 전부 선택 입력이다. Spring은
 * 현재 이 데이터의 출처가 없어(캐릭터 계약에 이름/빵타입 필드 자체가 없음) 항상 null로 보낸다 —
 * 데이터 출처가 생기면 채울 자리를 미리 만들어 둔 것이다.
 */
public record LetterRequestDto(
        Integer expectedRevision,
        String partnerBreadType,
        String representativeDoughType,
        String partnerSpeechStyle
) {
}
