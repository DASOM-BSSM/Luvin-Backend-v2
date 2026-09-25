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
@Table(name = "diary_room_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryRoomMember {

    @EmbeddedId
    private Pk id;

    @MapsId("roomId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private DiaryRoom room;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private DiaryRoomMemberRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    /** (room_id, user_id) 복합 PK — 같은 사람이 같은 방에 두 번 들어갈 수 없다. */
    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Pk implements Serializable {

        @Column(name = "room_id")
        private Long roomId;

        @Column(name = "user_id")
        private Long userId;
    }
}
