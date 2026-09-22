package com.luvin.simulation.repository;

import com.luvin.simulation.domain.SimulationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimulationReportRepository extends JpaRepository<SimulationReport, Long> {
    Optional<SimulationReport> findFirstByMemberIdOrderByCreatedAtDesc(Long memberId);
}
