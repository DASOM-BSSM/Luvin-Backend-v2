package com.luvin.diary.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.function.BooleanSupplier;

@Entity
@Table(name = "diary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 일기는 항상 공유방 하나 안에서 쓴다. 볼 수 있는 사람은 그 방의 방장·멤버다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private DiaryRoom room;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Diary(Long userId, DiaryRoom room, String title, String content) {
        this.userId = userId;
        this.room = room;
        this.title = title;
        this.content = content;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public boolean isWrittenBy(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 이 사용자가 일기를 볼 수 있는지 판단한다. 반응, 댓글, 상세 조회에서 같이 쓴다.
     * 작성자이거나, 일기가 속한 방의 방장·멤버면 볼 수 있다.
     *
     * 방 멤버 여부는 엔티티가 직접 조회할 수 없어서 호출하는 쪽(서비스)이 넘긴다.
     * 작성자·방장이 아닐 때만 실제로 호출되므로, 멤버 조회 쿼리도 그때만 나간다.
     */
    public boolean canBeViewedBy(Long userId, BooleanSupplier isRoomMember) {
        return isWrittenBy(userId) || room.isOwnedBy(userId) || isRoomMember.getAsBoolean();
    }
}
