package com.luvin.simulation.service;

import com.luvin.common.exception.ReportNotFoundException;
import com.luvin.simulation.domain.SimulationReport;
import com.luvin.simulation.dto.ReportData;
import com.luvin.simulation.repository.SimulationReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final SimulationReportRepository simulationReportRepository;

    @Override
    @Transactional(readOnly = true)
    public ReportData getReport(Long memberId) {
        SimulationReport report = simulationReportRepository.findFirstByMemberIdOrderByCreatedAtDesc(memberId)
                .orElseThrow(() -> new ReportNotFoundException(memberId));
        return new ReportData(report.getSummary(), report.getStrength(), report.getWeakness(), report.getAdvice());
    }

    @Override
    @Transactional
    public void saveReport(Long memberId, ReportData request) {
        simulationReportRepository.save(new SimulationReport(
                memberId, request.getSummary(), request.getStrength(), request.getWeakness(), request.getAdvice()));
    }
}
