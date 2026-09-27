package com.luvin.ai.client.dto;

import java.util.List;
import java.util.UUID;

public record ReportResponseDto(
        UUID finalPartnerId,
        String narrative,
        List<HighlightResponseDto> highlights,
        List<UUID> evidence,
        String renderMode
) {
}
