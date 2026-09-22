package com.luvin.simulation.repository;

import com.luvin.simulation.domain.Highlight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HighlightRepository extends JpaRepository<Highlight, Long> {
    List<Highlight> findAllByMemberIdOrderByImportanceDesc(Long memberId);
}
