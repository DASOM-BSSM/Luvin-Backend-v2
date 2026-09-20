package com.luvin.analysis.service;

import com.luvin.analysis.dto.ContentRecommendationListResponse;

public interface ContentService {
    ContentRecommendationListResponse getRecommendations();
}