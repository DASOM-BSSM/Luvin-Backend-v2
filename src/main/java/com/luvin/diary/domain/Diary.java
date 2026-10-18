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

    /** 공유방에 올린 일기만 값이 있다. 일기 1개는 공유방 최대 1개에 속한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private DiaryRoom room;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private DiaryVisibility visibility;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Diary(Long userId, DiaryRoom room, String title, String content, DiaryVisibility visibility) {
        this.userId = userId;
        this.room = room;
        this.title = title;
        this.content = content;
        this.visibility = visibility;
    }

    public void update(String title, String content, DiaryVisibility visibility) {
        this.title = title;
        this.content = content;
        this.visibility = visibility;
    }

    public boolean isWrittenBy(Long userId) {
        return this.userId.equals(userId);
    }

    /**
     * 공개범위 기준으로 이 사용자가 일기를 볼 수 있는지 판단한다. 공감, 댓글, 상세 조회에서 같이 쓴다.
     *
     * 방 멤버 여부는 엔티티가 직접 조회할 수 없어서 호출하는 쪽(서비스)이 넘긴다.
     * ROOM 일기일 때만 실제로 호출되므로, 멤버 조회 쿼리도 그때만 나간다.
     */
    public boolean canBeViewedBy(Long userId, BooleanSupplier isRoomMember) {
        if (isWrittenBy(userId)) {
            return true;
        }
        return switch (visibility) {
            case PUBLIC -> true;
            case ROOM -> room != null && isRoomMember.getAsBoolean();
            case PRIVATE -> false;
        };
    }
}
