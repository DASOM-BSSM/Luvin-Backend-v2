package com.luvin.ai.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiReportView(
        UUID finalPartnerId,
        String narrative,
        List<Map<String, String>> highlights,
        String renderMode
) {
}
