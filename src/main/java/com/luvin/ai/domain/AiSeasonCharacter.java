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

    /** 장면 블록 회차의 출연진 역할. 새 흐름을 시작할 때 배정하고, 기존 5화 흐름만 진행한 시즌은 null이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cast_role", length = 20)
    private AiCastRole castRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_style", length = 20)
    private AiAttachmentStyle attachmentStyle;

    /** 출연진 표시 이름. 팀이 출연진 정의를 전달하기 전까지는 placeholder 이름이 들어간다. */
    @Column(name = "name", length = 50)
    private String name;

    public AiSeasonCharacter(AiSeason season, UUID characterId, AiCharacterRole role, String gender) {
        this.season = season;
        this.characterId = characterId;
        this.role = role;
        this.gender = gender;
    }

    /** Spring이 추가하는 출연진(친구, 분위기 메이커). AI 시즌에는 없는 캐릭터라 id도 Spring이 만든다. */
    public AiSeasonCharacter(AiSeason season, UUID characterId, AiCastRole castRole, String name) {
        this(season, characterId, AiCharacterRole.SUPPORTING, null);
        assignCast(castRole, name);
    }

    public void assignCast(AiCastRole castRole, String name) {
        this.castRole = castRole;
        this.attachmentStyle = castRole.attachmentStyle();
        this.name = name;
    }

    /** 개발 전용 초기화에서 출연진 배정을 되돌린다. */
    public void clearCast() {
        this.castRole = null;
        this.attachmentStyle = null;
        this.name = null;
    }

    public boolean isRepresentative() {
        return role == AiCharacterRole.REPRESENTATIVE;
    }
}
