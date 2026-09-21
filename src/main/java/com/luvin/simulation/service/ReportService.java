package com.luvin.simulation.service;

import com.luvin.simulation.dto.ReportData;

public interface ReportService {
    ReportData getReport(Long memberId);
    void saveReport(Long memberId, ReportData request);
}
