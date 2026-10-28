package com.luvin.ai.repository;

import com.luvin.ai.domain.AiEpisodeContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiEpisodeContentRepository extends JpaRepository<AiEpisodeContent, Long> {
    Optional<AiEpisodeContent> findBySeason_IdAndEpisodeOrder(Long seasonPk, Integer episodeOrder);

    List<AiEpisodeContent> findAllBySeason_IdOrderByEpisodeOrderAsc(Long seasonPk);

    void deleteAllBySeason_Id(Long seasonPk);

    /**
     * 생성 진행 표시를 바꾼다. 같은 생성 회차이고 아직 생성 중일 때만 바꾼다. 벌크 update라 row 버전을 올리지 않아, 같은 row를
     * 저장하는 다른 요청과 낙관적 락 충돌을 만들지 않는다.
     */
    @Modifying
    @Query("""
            update AiEpisodeContent c
               set c.generationStage = :stage, c.generationJobId = :jobId, c.generationRegenerating = :regenerating
             where c.id = :id and coalesce(c.generationRound, 0) = :round
               and c.status = com.luvin.ai.domain.AiStoryContentStatus.GENERATING
            """)
    int updateGenerationProgress(@Param("id") Long id, @Param("round") int round, @Param("stage") String stage,
                                 @Param("jobId") UUID jobId, @Param("regenerating") boolean regenerating);

    /** 완료 진행 표시를 바꾼다. 같은 완료 회차이고 아직 완료 중일 때만 바꾼다(벌크 update, 버전 그대로). */
    @Modifying
    @Query("""
            update AiEpisodeContent c
               set c.completionStage = :stage, c.completionJobId = :jobId, c.completionRegenerating = :regenerating
             where c.id = :id and coalesce(c.completionRound, 0) = :round
               and c.completedAt is null and c.completionErrorCode is null
            """)
    int updateCompletionProgress(@Param("id") Long id, @Param("round") int round, @Param("stage") String stage,
                                 @Param("jobId") UUID jobId, @Param("regenerating") boolean regenerating);

    /** 기동 때 이어서 진행할 생성: 생성 중이고 staleBefore 이후에 시작했다. */
    @Query("""
            select c from AiEpisodeContent c
             where c.status = com.luvin.ai.domain.AiStoryContentStatus.GENERATING and c.requestedAt >= :staleBefore
            """)
    List<AiEpisodeContent> findGeneratingSince(@Param("staleBefore") LocalDateTime staleBefore);

    /** 기동 때 이어서 진행할 완료: 완료 중이고 staleBefore 이후에 시작했다. */
    @Query("""
            select c from AiEpisodeContent c
             where c.completedAt is null and c.completionErrorCode is null and c.completionRequestedAt >= :staleBefore
            """)
    List<AiEpisodeContent> findCompletingSince(@Param("staleBefore") LocalDateTime staleBefore);
}
