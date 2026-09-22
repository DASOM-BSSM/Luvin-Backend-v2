package com.luvin.ai.client.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReportResponseDto(
        UUID finalPartnerId,
        String narrative,
        List<Map<String, String>> highlights,
        List<UUID> evidence,
        String renderMode
) {
}
