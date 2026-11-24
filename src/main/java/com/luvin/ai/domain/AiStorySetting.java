package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 러빈지옥 운영 설정 한 건(재배포 없이 바꾸는 값). 행이 없으면 애플리케이션 설정값을 쓴다.
 * 테이블은 Flyway V24가 만든다.
 */
@Entity
@Table(name = "ai_story_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiStorySetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AiStorySetting(String key, String value) {
        this.key = key;
        change(value);
    }

    public void change(String value) {
        this.value = value;
        this.updatedAt = LocalDateTime.now();
    }
}
