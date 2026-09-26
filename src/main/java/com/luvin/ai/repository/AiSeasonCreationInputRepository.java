package com.luvin.ai.repository;

import com.luvin.ai.domain.AiSeasonCreationInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSeasonCreationInputRepository extends JpaRepository<AiSeasonCreationInput, Long> {
    Optional<AiSeasonCreationInput> findByMemberId(Long memberId);
}
