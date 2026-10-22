package com.luvin.ai.repository;

import com.luvin.ai.domain.AiSeasonStoryReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSeasonStoryReportRepository extends JpaRepository<AiSeasonStoryReport, Long> {
    Optional<AiSeasonStoryReport> findBySeason_Id(Long seasonPk);

    void deleteAllBySeason_Id(Long seasonPk);
}
