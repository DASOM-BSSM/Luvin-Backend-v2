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

/** 사용자별 말투 요약(JSON). AI 말투 추출 결과를 이전 값과 섞어 조금씩 바꾼다(SpeechStyleBlender). */
@Entity
@Table(name = "user_speech_style")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSpeechStyle {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style_json", nullable = false, columnDefinition = "jsonb")
    private String styleJson;

    @Column(name = "update_count", nullable = false)
    private int updateCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserSpeechStyle(Long userId, String styleJson) {
        this.userId = userId;
        this.styleJson = styleJson;
        this.updateCount = 1;
        this.updatedAt = OffsetDateTime.now();
    }

    public void update(String styleJson) {
        this.styleJson = styleJson;
        this.updateCount++;
        this.updatedAt = OffsetDateTime.now();
    }
}
