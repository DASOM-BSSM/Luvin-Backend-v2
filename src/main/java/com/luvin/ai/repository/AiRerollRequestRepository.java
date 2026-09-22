package com.luvin.ai.repository;

import com.luvin.ai.domain.AiJobStatus;
import com.luvin.ai.domain.AiRerollRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiRerollRequestRepository extends JpaRepository<AiRerollRequest, Long> {
    Optional<AiRerollRequest> findByIdempotencyKey(UUID idempotencyKey);

    List<AiRerollRequest> findAllByJobStatusIn(List<AiJobStatus> statuses);

    List<AiRerollRequest> findAllByJobStatusInAndNextPollAtLessThanEqual(
            List<AiJobStatus> statuses, LocalDateTime now);
}
