package com.luvin.ai.repository;

import com.luvin.ai.domain.AiBreadRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiBreadRelationRepository extends JpaRepository<AiBreadRelation, Long> {
    List<AiBreadRelation> findAllBySeason_Id(Long seasonPk);

    Optional<AiBreadRelation> findBySeason_IdAndTargetBreadId(Long seasonPk, UUID targetBreadId);

    void deleteAllBySeason_Id(Long seasonPk);
}
