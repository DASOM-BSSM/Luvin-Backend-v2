package com.luvin.ai.repository;

import com.luvin.ai.domain.AiSeasonChoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiSeasonChoiceRepository extends JpaRepository<AiSeasonChoice, Long> {
    List<AiSeasonChoice> findAllBySeason_IdOrderByPointIdAsc(Long seasonPk);

    Optional<AiSeasonChoice> findBySeason_IdAndPointId(Long seasonPk, Integer pointId);

    void deleteAllBySeason_Id(Long seasonPk);
}
