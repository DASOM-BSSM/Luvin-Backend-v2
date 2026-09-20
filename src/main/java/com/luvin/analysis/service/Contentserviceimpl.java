package com.luvin.analysis.service;

import com.luvin.analysis.dto.AnalysisSummaryResponse;
import com.luvin.analysis.service.AnalysisService;
import com.luvin.analysis.domain.Content;
import com.luvin.analysis.dto.ContentRecommendationListResponse;
import com.luvin.analysis.dto.ContentRecommendationResponse;
import com.luvin.analysis.repository.ContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContentServiceImpl implements ContentService {

    private final ContentRepository contentRepository;
    private final AnalysisService analysisService;

    public ContentServiceImpl(ContentRepository contentRepository, AnalysisService analysisService) {
        this.contentRepository = contentRepository;
        this.analysisService = analysisService;
    }

    @Override
    @Transactional(readOnly = true)
    public ContentRecommendationListResponse getRecommendations() {

        AnalysisSummaryResponse summary = analysisService.getSummary();
        String datingStyle = summary.datingStyle();

        List<Content> matched = contentRepository.findAllByTargetDatingStyle(datingStyle);
        List<Content> common = contentRepository.findAllByTargetDatingStyleIsNull();

        List<ContentRecommendationResponse> contents = new ArrayList<>();
        matched.forEach(content -> contents.add(
                ContentRecommendationResponse.of(content, "사용자의 " + datingStyle + " 성향과 관련된 콘텐츠입니다.")
        ));
        common.forEach(content -> contents.add(
                ContentRecommendationResponse.of(content, "누구에게나 도움이 되는 콘텐츠입니다.")
        ));

        return new ContentRecommendationListResponse(contents);
    }
}