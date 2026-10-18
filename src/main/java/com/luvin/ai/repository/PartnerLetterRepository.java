package com.luvin.ai.repository;

import com.luvin.ai.domain.PartnerLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartnerLetterRepository extends JpaRepository<PartnerLetter, Long> {
    Optional<PartnerLetter> findBySeason_Id(Long seasonPk);

    /**
     * 안 읽은 쪽지가 여러 개 쌓여도 최신 1개만 돌려준다(요구사항) — 응답에 seasonId(UUID, AiSeason의
     * 연관관계)가 필요해 JOIN FETCH로 함께 가져온다. 트랜잭션 밖에서 season을 지연 로딩하면
     * LazyInitializationException이 나기 때문이다.
     */
    @Query("SELECT l FROM PartnerLetter l JOIN FETCH l.season WHERE l.memberId = :memberId AND l.read = false "
            + "ORDER BY l.createdAt DESC")
    List<PartnerLetter> findUnreadOrderByCreatedAtDesc(@Param("memberId") Long memberId);

    /** 본인 소유가 아닌 letterId는 존재 자체를 노출하지 않기 위해 memberId까지 함께 조회한다. */
    Optional<PartnerLetter> findByIdAndMemberId(Long id, Long memberId);
}
