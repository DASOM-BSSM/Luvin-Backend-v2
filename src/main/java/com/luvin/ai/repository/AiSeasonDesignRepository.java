package com.luvin.ai.repository;

import com.luvin.ai.domain.AiSeasonDesign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSeasonDesignRepository extends JpaRepository<AiSeasonDesign, Long> {
    Optional<AiSeasonDesign> findBySeason_Id(Long seasonPk);

    void deleteAllBySeason_Id(Long seasonPk);
}
