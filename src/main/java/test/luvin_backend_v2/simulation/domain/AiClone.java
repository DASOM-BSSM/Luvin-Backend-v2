package com.luvin.simulation.domain;

import com.luvin.user.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_clones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiClone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String cloneName;
    private String speakingStyle;
    private String datingStyle;
    private String personaSummary;

    @Builder
    public AiClone(User user, String cloneName, String speakingStyle, String datingStyle, String personaSummary) {
        this.user = user;
        this.cloneName = cloneName;
        this.speakingStyle = speakingStyle;
        this.datingStyle = datingStyle;
        this.personaSummary = personaSummary;
    }
}