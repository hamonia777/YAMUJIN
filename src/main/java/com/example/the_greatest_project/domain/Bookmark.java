package com.example.the_greatest_project.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookmark", indexes = @Index(name = "idx_bm_user", columnList = "userId"))
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 30)
    private String kind = "NEWS";      // NEWS | PRODUCT | BRIEFING

    @Column(length = 500)
    private String title;

    @Column(length = 1000)
    private String url;

    @Column(length = 120)
    private String source;

    @Column(length = 2000)
    private String memo;

    @Column(length = 2000)
    private String payload;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
