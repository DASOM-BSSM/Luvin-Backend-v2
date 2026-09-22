package com.luvin.ai.repository;

import com.luvin.ai.domain.AiCharacterRole;
import com.luvin.ai.domain.AiSeasonCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSeasonCharacterRepository extends JpaRepository<AiSeasonCharacter, Long> {
    List<AiSeasonCharacter> findAllBySeason_Id(Long seasonPk);

    Optional<AiSeasonCharacter> findBySeason_IdAndCharacterId(Long seasonPk, UUID characterId);

    Optional<AiSeasonCharacter> findFirstBySeason_IdAndRole(Long seasonPk, AiCharacterRole role);
}
