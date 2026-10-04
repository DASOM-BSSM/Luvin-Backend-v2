package com.luvin.diary.domain;

import com.luvin.user.domain.User;
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

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User member;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private DiaryRoomMemberRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    public DiaryRoomMember(DiaryRoom room, User member, DiaryRoomMemberRole role) {
        this.id = new Pk(room.getId(), member.getId());
        this.room = room;
        this.member = member;
        this.role = role != null ? role : DiaryRoomMemberRole.MEMBER;
    }

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

        public Pk(Long roomId, Long userId) {
            this.roomId = roomId;
            this.userId = userId;
        }
    }
}
