package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 장면 블록 회차의 1:1 장면(pair 블록) 메시지 좋아요. row가 있으면 좋아요 상태다(취소하면 지운다).
 * 내 빵의 대사에 누른 좋아요는 다음 회차 생성 때 FastAPI 입력(learnedProfile.likedMessages)으로 들어간다.
 */
@Entity
@Table(name = "ai_story_message_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "message_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiStoryMessageLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "episode_order", nullable = false)
    private Integer episodeOrder;

    @Column(name = "block_id", nullable = false, length = 20)
    private String blockId;

    @Column(name = "message_id", nullable = false, columnDefinition = "uuid")
    private UUID messageId;

    @Column(name = "message_text", nullable = false, columnDefinition = "text")
    private String messageText;

    /** 내 빵의 대사인지. 학습 입력(likedMessages)에는 내 빵의 대사만 넣는다. */
    @Column(name = "from_me", nullable = false)
    private boolean fromMe;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiStoryMessageLike(AiSeason season, int episodeOrder, String blockId, UUID messageId, String messageText,
                              boolean fromMe) {
        this.season = season;
        this.episodeOrder = episodeOrder;
        this.blockId = blockId;
        this.messageId = messageId;
        this.messageText = messageText;
        this.fromMe = fromMe;
        this.createdAt = LocalDateTime.now();
    }
}
