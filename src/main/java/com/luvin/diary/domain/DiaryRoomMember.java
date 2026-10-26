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
@Table(name = "diary_room_member", uniqueConstraints = @UniqueConstraint(
        name = "uk_diary_room_member_room_nickname", columnNames = {"room_id", "nickname"}))
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

    /**
     * 이 방에서 쓰는 닉네임(1~10자). 계정 닉네임과 별개이고, 같은 방 안에서는 겹치지 않는다.
     * 서비스가 방 행을 잠그고 먼저 확인해 409로 막고, DB UNIQUE(room_id, nickname)가 마지막으로 보장한다(Flyway V13).
     */
    @Column(name = "nickname", nullable = false, columnDefinition = "text")
    private String nickname;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    public DiaryRoomMember(DiaryRoom room, User member, DiaryRoomMemberRole role, String nickname) {
        this.id = new Pk(room.getId(), member.getId());
        this.room = room;
        this.member = member;
        this.role = role != null ? role : DiaryRoomMemberRole.MEMBER;
        this.nickname = nickname;
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
