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

    /** 반응한 이모지 문자 그대로 (예: "❤", "😂"). Emoji.normalize()를 거친 값만 저장한다. */
    @Column(name = "emoji", nullable = false, length = 32)
    private String emoji;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** (diary_id, user_id) 복합 PK — 한 사람은 한 일기에 반응을 1개만 남길 수 있다. */
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
