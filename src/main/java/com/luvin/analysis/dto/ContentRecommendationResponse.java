package com.luvin.analysis.dto;

import com.luvin.analysis.domain.Content;

public record ContentRecommendationResponse(
        Long contentId,
        String title,
        String description,
        String contentType,
        String thumbnailUrl,
        String url,
        String reason
) {
    public static ContentRecommendationResponse of(Content content, String reason) {
        return new ContentRecommendationResponse(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                content.getContentType().name(),
                content.getThumbnailUrl(),
                content.getUrl(),
                reason
        );
    }
}