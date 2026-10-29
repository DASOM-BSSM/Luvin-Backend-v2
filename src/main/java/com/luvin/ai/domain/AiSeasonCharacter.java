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

    /** 출연진 표시 이름. 내 빵은 사용자 닉네임, 나머지는 시즌 시작 때 정한 "형용사 + 빵 종류"다(2시즌부터는 새 시즌 설정의 이름). */
    @Column(name = "name", length = 50)
    private String name;

    /** 빵 종류(설문 빵 타입의 한글 이름, 예: 소금빵). 시즌 안에서 출연진끼리 겹치지 않는다. */
    @Column(name = "bread_type", length = 30)
    private String breadType;

    /** 연애 후보 셋의 순서(1~3). 시즌 시작 때 랜덤으로 정하고, 1~3화 1:1 상대가 이 순서를 따른다. 그 외 출연진은 null. */
    @Column(name = "candidate_order")
    private Integer candidateOrder;

    public AiSeasonCharacter(AiSeason season, UUID characterId, AiCharacterRole role, String gender) {
        this.season = season;
        this.characterId = characterId;
        this.role = role;
        this.gender = gender;
    }

    /** Spring이 추가하는 출연진(친구, 분위기 메이커). AI 시즌에는 없는 캐릭터라 id도 Spring이 만든다. */
    public AiSeasonCharacter(AiSeason season, UUID characterId, AiCastRole castRole, String name, String breadType,
                             String gender) {
        this(season, characterId, AiCharacterRole.SUPPORTING, gender);
        assignCast(castRole, name, breadType, gender, null);
    }

    /** 성별은 사용자 성별 형식(MALE/FEMALE)으로 저장한다: 친구는 사용자와 같게, 나머지 후보·분위기 메이커는 다르게. */
    public void assignCast(AiCastRole castRole, String name, String breadType, String gender, Integer candidateOrder) {
        this.castRole = castRole;
        this.attachmentStyle = castRole.attachmentStyle();
        this.name = name;
        this.breadType = breadType;
        this.gender = gender;
        this.candidateOrder = candidateOrder;
    }

    /** 2시즌부터: 새 시즌 설정이 정한 이름·빵 종류로 바꾼다. 역할·성별·만나는 순서는 그대로다. */
    public void renameForSeasonDesign(String name, String breadType) {
        this.name = name;
        this.breadType = breadType;
    }

    /** 개발 전용 초기화에서 출연진 배정을 되돌린다. */
    public void clearCast() {
        this.castRole = null;
        this.attachmentStyle = null;
        this.name = null;
        this.breadType = null;
        this.candidateOrder = null;
    }

    public boolean isRepresentative() {
        return role == AiCharacterRole.REPRESENTATIVE;
    }
}
