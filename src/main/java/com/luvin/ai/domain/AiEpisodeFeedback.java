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
 * "나의 빵에게" — 2화/4화 1:1 대화 중 본인 AI 분신(representative)이 한 말 한 줄을 골라 쓰는
 * 피드백. 회차당 정확히 1개만 허용한다(season_id + episode_number unique) — 화면 흐름이
 * "한 줄 고르기 → 이유 보기 → 글쓰기 → 보내기" 1회성 동작이기 때문이다.
 */
@Entity
@Table(name = "ai_episode_feedbacks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "episode_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiEpisodeFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    /** 피드백 대상으로 고른 메시지. AI 시즌 대화는 Spring에 로컬 복사본이 없어 FK가 아니다. */
    @Column(name = "message_id", nullable = false, columnDefinition = "uuid")
    private UUID messageId;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiEpisodeFeedback(AiSeason season, Integer episodeNumber, UUID messageId, String content) {
        this.season = season;
        this.episodeNumber = episodeNumber;
        this.messageId = messageId;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}
