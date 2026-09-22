package com.luvin.ai.dto;

import java.util.UUID;

public record AiSelectionView(
        UUID selectionId,
        UUID partnerId,
        Integer revision
) {
}
