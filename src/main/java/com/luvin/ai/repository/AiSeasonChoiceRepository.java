package com.luvin.ai.repository;

import com.luvin.ai.domain.AiSeasonChoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiSeasonChoiceRepository extends JpaRepository<AiSeasonChoice, Long> {
    List<AiSeasonChoice> findAllBySeason_IdOrderByPointIdAsc(Long seasonPk);

    Optional<AiSeasonChoice> findBySeason_IdAndPointId(Long seasonPk, Integer pointId);

    /** 결과 장면 생성을 다시 시작할 때 같은 선택을 동시에 두 요청이 다시 시작하지 않도록 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AiSeasonChoice c where c.season.id = :seasonPk and c.pointId = :pointId")
    Optional<AiSeasonChoice> findWithLockBySeasonPkAndPointId(@Param("seasonPk") Long seasonPk,
                                                             @Param("pointId") Integer pointId);

    /** 기동 때 이어서 진행할 결과 장면 생성: 생성 중이고 staleBefore 이후에 시작했다. */
    @Query("""
            select c from AiSeasonChoice c
             where c.resultStatus = com.luvin.ai.domain.AiStoryContentStatus.GENERATING
               and c.resultRequestedAt >= :staleBefore
            """)
    List<AiSeasonChoice> findResultGeneratingSince(@Param("staleBefore") LocalDateTime staleBefore);

    void deleteAllBySeason_Id(Long seasonPk);
}
