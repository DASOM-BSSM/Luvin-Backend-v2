package com.luvin.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contents")
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(nullable = false)
    private String url;

    // 이 콘텐츠가 특정 연애 성향(AnalysisResult.datingStyle: 안정형/직진형/신중형/균형형)과 매칭될 때만 추천되도록 하는 태그.
    // null이면 성향과 무관하게 모두에게 추천되는 공통 콘텐츠로 취급한다.
    @Column(name = "target_dating_style")
    private String targetDatingStyle;

    protected Content() {
    }

    public Content(String title, String description, ContentType contentType, String thumbnailUrl, String url, String targetDatingStyle) {
        this.title = title;
        this.description = description;
        this.contentType = contentType;
        this.thumbnailUrl = thumbnailUrl;
        this.url = url;
        this.targetDatingStyle = targetDatingStyle;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ContentType getContentType() { return contentType; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getUrl() { return url; }
    public String getTargetDatingStyle() { return targetDatingStyle; }
}