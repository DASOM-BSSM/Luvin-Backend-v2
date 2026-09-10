package test.luvin_backend_v2.analysis.dto;

import java.util.List;

public record AnalysisReportResponse(
        String nickname,
        String summary,
        List<String> strengths,
        List<String> cautions
) {
}