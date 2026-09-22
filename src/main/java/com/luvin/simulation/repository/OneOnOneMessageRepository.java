package com.luvin.simulation.repository;

import com.luvin.simulation.domain.OneOnOneMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OneOnOneMessageRepository extends JpaRepository<OneOnOneMessage, Long> {
    List<OneOnOneMessage> findAllByMatch_MatchIdOrderBySequenceAsc(Long matchId);
}
