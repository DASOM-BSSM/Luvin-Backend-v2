package com.luvin.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 반죽 성격 보정: 성격 축(설문 차원 key) → 설문 점수 위에 더할 누적 변화량(1~100 척도).
 * 설문 결과가 기본값이고, 이 값은 PersonalityAdjuster의 상한 안에서만 움직인다. 빵 타입은 바꾸지 않는다.
 */
@Entity
@Table(name = "user_personality_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPersonalityAdjustment {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deltas_json", nullable = false, columnDefinition = "jsonb")
    private String deltasJson;

    @Column(name = "update_count", nullable = false)
    private int updateCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserPersonalityAdjustment(Long userId, String deltasJson) {
        this.userId = userId;
        this.deltasJson = deltasJson;
        this.updateCount = 1;
        this.updatedAt = OffsetDateTime.now();
    }

    public void update(String deltasJson) {
        this.deltasJson = deltasJson;
        this.updateCount++;
        this.updatedAt = OffsetDateTime.now();
    }
}
