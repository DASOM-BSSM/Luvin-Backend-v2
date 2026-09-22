package com.luvin.simulation.domain;

import com.luvin.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_clones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiClone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "clone_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "clone_name", nullable = false, length = 100)
    private String cloneName;

    @Column(name = "speaking_style", length = 100)
    private String speakingStyle;

    @Column(name = "dating_style", length = 50)
    private String datingStyle;

    @Column(name = "persona_summary", length = 1000)
    private String personaSummary;
}
