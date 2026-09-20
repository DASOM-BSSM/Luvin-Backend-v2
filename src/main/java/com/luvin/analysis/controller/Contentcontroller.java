package com.luvin.analysis.controller;

import com.luvin.analysis.dto.ContentRecommendationListResponse;
import com.luvin.analysis.service.ContentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/recommendations")
    public ContentRecommendationListResponse getRecommendations() {
        return contentService.getRecommendations();
    }
}