package com.luvin.simulation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특정 에피소드에 등장하는 "빵" 한 명.
 * SELF면 character는 null이고 name은 유저의 AiClone 이름 스냅샷,
 * CAST면 character가 SimulationCharacter를 가리키고 name은 그 캐릭터 이름 스냅샷이다.
 */
@Entity
@Table(name = "episode_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long participantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 10)
    private ParticipantType participantType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    private SimulationCharacter character;

    @Column(nullable = false, length = 50)
    private String name;

    /**
     * name과 동일하게 생성 시점 스냅샷이다. SELF는 User.gender, CAST는 SimulationCharacter.gender.
     * User.gender가 아직 없는 유저(현재 PUT /api/users/me에 gender 설정 경로가 없어 null일 수 있음)는
     * null로 저장될 수 있다.
     */
    @Column(length = 10)
    private String gender;

    public EpisodeParticipant(Episode episode, ParticipantType participantType,
                               SimulationCharacter character, String name, String gender) {
        this.episode = episode;
        this.participantType = participantType;
        this.character = character;
        this.name = name;
        this.gender = gender;
    }

    public boolean isSelf() {
        return participantType == ParticipantType.SELF;
    }
}
