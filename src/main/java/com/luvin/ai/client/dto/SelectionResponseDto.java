package com.luvin.ai.client.dto;

import java.util.UUID;

public record SelectionResponseDto(
        UUID selectionId,
        UUID selectedPartnerId,
        Integer revision
) {
}
