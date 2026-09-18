package com.luvin.analysis.controller;

import com.luvin.analysis.dto.AiCloneResponse;
import com.luvin.analysis.dto.AnalysisChatRequest;
import com.luvin.analysis.dto.AnalysisChatResponse;
import com.luvin.analysis.dto.AnalysisReportResponse;
import com.luvin.analysis.dto.AnalysisSummaryResponse;
import com.luvin.analysis.service.AnalysisService;
import com.luvin.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping
    public ApiResponse<AnalysisSummaryResponse> getSummary() {
        return ApiResponse.ok(analysisService.getSummary());
    }

    @PostMapping
    public ApiResponse<AnalysisSummaryResponse> generateAnalysis() {
        return ApiResponse.ok(analysisService.generateAnalysis());
    }

    @GetMapping("/report")
    public ApiResponse<AnalysisReportResponse> getReport() {
        return ApiResponse.ok(analysisService.getReport());
    }

    @GetMapping("/clone")
    public ApiResponse<AiCloneResponse> getClone() {
        return ApiResponse.ok(analysisService.getClone());
    }

    @PostMapping("/chat")
    public ApiResponse<AnalysisChatResponse> chat(@Valid @RequestBody AnalysisChatRequest request) {
        return ApiResponse.ok(analysisService.chat(request));
    }
}