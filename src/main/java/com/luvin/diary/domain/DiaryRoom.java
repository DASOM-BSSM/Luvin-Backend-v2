package com.luvin.diary.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "diary_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * 초대 코드 (영구, 8자리). 이 코드를 아는 사람은 누구나 방에 참여할 수 있다.
     * 방을 만들 때 발급한다. 이 컬럼이 생기기 전에 있던 방은 Flyway V7이 코드를 채우고 NOT NULL/UNIQUE를 건다.
     */
    @Column(name = "invite_code", nullable = false, length = 8, unique = true)
    private String inviteCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public DiaryRoom(String name, String description, Long ownerId) {
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void changeInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 방장 확인은 owner_id 하나로만 한다. */
    public boolean isOwnedBy(Long userId) {
        return this.ownerId != null && this.ownerId.equals(userId);
    }
}
