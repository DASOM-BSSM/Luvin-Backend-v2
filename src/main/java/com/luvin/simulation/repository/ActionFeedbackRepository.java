package com.luvin.simulation.repository;

import com.luvin.simulation.domain.ActionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActionFeedbackRepository extends JpaRepository<ActionFeedback, Long> {
    Optional<ActionFeedback> findByMemberIdAndMessage_MessageId(Long memberId, Long messageId);
}
