package com.luvin.analysis.dto;

import java.util.List;

public record ContentRecommendationListResponse(
        List<ContentRecommendationResponse> contents
) {
}