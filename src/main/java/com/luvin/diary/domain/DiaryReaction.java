package com.luvin.diary.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "diary_reaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryReaction {

    @EmbeddedId
    private Pk id;

    @MapsId("diaryId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id")
    private Diary diary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** (diary_id, user_id) 복합 PK — 같은 사람이 같은 일기에 두 번 공감할 수 없다. */
    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Pk implements Serializable {

        @Column(name = "diary_id")
        private Long diaryId;

        @Column(name = "user_id")
        private Long userId;
    }
}
