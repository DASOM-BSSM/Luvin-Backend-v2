package com.luvin.ai.client.dto;

import java.util.UUID;

public record RerollRequestDto(
        Integer expectedRevision,
        UUID expectedVersionId,
        UUID partnerId
) {
}
