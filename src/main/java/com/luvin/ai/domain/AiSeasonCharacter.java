package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.util.UUID;

/**
 * 시즌 캐릭터 스냅샷. id, role, 표시 정보를 저장하고 representative 식별에 사용한다 (요구사항 4절).
 */
@Entity
@Table(name = "ai_season_characters",
        uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "character_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSeasonCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "character_id", nullable = false, columnDefinition = "uuid")
    private UUID characterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AiCharacterRole role;

    @Column(name = "gender", length = 10)
    private String gender;

    public AiSeasonCharacter(AiSeason season, UUID characterId, AiCharacterRole role, String gender) {
        this.season = season;
        this.characterId = characterId;
        this.role = role;
        this.gender = gender;
    }

    public boolean isRepresentative() {
        return role == AiCharacterRole.REPRESENTATIVE;
    }
}
