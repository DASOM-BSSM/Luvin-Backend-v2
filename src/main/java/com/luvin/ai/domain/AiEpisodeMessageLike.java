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
 * 2화/4화 1:1 대화 메시지 하트(좋아요). "나의 빵에게" 피드백(AiEpisodeFeedback, 회차당 1개)과
 * 달리 메시지마다 개별로 켜고 끌 수 있어 개수 제한이 없다 — 한 행이 "이 메시지는 좋아요 상태"를
 * 뜻하고, 끄면 행 자체를 삭제한다(row 존재 여부 = liked 상태). 내 분신/상대 메시지 모두 가능하다.
 *
 * 다시 굽기로 대화가 새 버전이 되면 메시지 UUID 자체가 새로 생기므로, 이전 버전의 좋아요
 * row는 자연히 새 대화 어디에도 매치되지 않아 보이지 않게 된다(별도 정리 로직 불필요).
 */
@Entity
@Table(name = "ai_episode_message_likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "episode_number", "message_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiEpisodeMessageLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    /** 좋아요 대상 메시지. AI 시즌 대화는 Spring에 로컬 복사본이 없어 FK가 아니다. */
    @Column(name = "message_id", nullable = false, columnDefinition = "uuid")
    private UUID messageId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiEpisodeMessageLike(AiSeason season, Integer episodeNumber, UUID messageId) {
        this.season = season;
        this.episodeNumber = episodeNumber;
        this.messageId = messageId;
        this.createdAt = LocalDateTime.now();
    }
}
