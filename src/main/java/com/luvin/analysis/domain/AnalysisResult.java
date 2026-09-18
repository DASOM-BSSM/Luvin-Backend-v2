package com.luvin.analysis.domain;

import com.luvin.user.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "analysis_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String title;
    private String datingStyle;
    private String description;
    private String summary;
    private int expressionScore;
    private int stabilityScore;
    private int conflictResolutionScore;

    @ElementCollection
    @CollectionTable(name = "analysis_strengths", joinColumns = @JoinColumn(name = "analysis_id"))
    private List<String> strengths = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "analysis_cautions", joinColumns = @JoinColumn(name = "analysis_id"))
    private List<String> cautions = new ArrayList<>();

    @Builder
    public AnalysisResult(
            User user,
            String title,
            String datingStyle,
            String description,
            String summary,
            int expressionScore,
            int stabilityScore,
            int conflictResolutionScore,
            List<String> strengths,
            List<String> cautions
    ) {
        this.user = user;
        this.title = title;
        this.datingStyle = datingStyle;
        this.description = description;
        this.summary = summary;
        this.expressionScore = expressionScore;
        this.stabilityScore = stabilityScore;
        this.conflictResolutionScore = conflictResolutionScore;
        this.strengths = strengths;
        this.cautions = cautions;
    }
}