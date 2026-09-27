package com.luvin.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiReportView(
        UUID finalPartnerId,
        String narrative,
        List<AiHighlightView> highlights,
        String renderMode
) {
}
