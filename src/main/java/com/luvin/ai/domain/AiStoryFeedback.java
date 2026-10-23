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
 * 장면 블록 회차의 1:1 장면(pair 블록)에서 내 빵의 대사에 남긴 피드백. 메시지당 1개이고 다시 남기면 덮어쓴다.
 * 다음 회차 생성 때 FastAPI 입력(learnedProfile.feedbacks)으로 들어간다. messageText는 그때 보낼 대사 원문이다.
 */
@Entity
@Table(name = "ai_story_feedbacks", uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "message_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiStoryFeedback {

    public static final int TEXT_MAX = 200;

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

    @Column(name = "text", nullable = false, length = TEXT_MAX)
    private String text;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AiStoryFeedback(AiSeason season, int episodeOrder, String blockId, UUID messageId, String messageText,
                           String text) {
        this.season = season;
        this.episodeOrder = episodeOrder;
        this.blockId = blockId;
        this.messageId = messageId;
        this.messageText = messageText;
        this.text = text;
        this.updatedAt = LocalDateTime.now();
    }

    public void rewrite(String text) {
        this.text = text;
        this.updatedAt = LocalDateTime.now();
    }
}
