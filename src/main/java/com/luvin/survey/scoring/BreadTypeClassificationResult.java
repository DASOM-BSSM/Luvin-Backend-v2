package com.luvin.survey.scoring;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record BreadTypeClassificationResult(
        String primaryType,
        String secondaryType,
        boolean mixed,
        boolean poorFit,
        boolean tie,
        BigDecimal topTwoDistanceGap,
        Map<String, BigDecimal> distances,
        List<ReasonEvidence> reasonEvidence
) {
}
