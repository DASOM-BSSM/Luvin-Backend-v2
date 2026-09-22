package com.luvin.simulation.controller;

import com.luvin.common.response.MessageResponse;
import com.luvin.common.security.SecurityUtils;
import com.luvin.simulation.dto.ReportData;
import com.luvin.simulation.service.ReportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ReportData getReport() {
        return reportService.getReport(SecurityUtils.getCurrentUserId());
    }

    @PostMapping
    public MessageResponse saveReport(@RequestBody ReportData body) {
        reportService.saveReport(SecurityUtils.getCurrentUserId(), body);
        return new MessageResponse("리포트 저장 완료");
    }
}
