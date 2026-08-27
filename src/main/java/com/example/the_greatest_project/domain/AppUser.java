package com.example.the_greatest_project.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user", indexes = @Index(name = "idx_user_email", columnList = "email", unique = true))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(nullable = false, length = 60)
    private String nickname;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 20)
    private String role = "USER";

    @Column(length = 500)
    private String interests = "AI,경제,정치,반도체";

    @Column(length = 20)
    private String theme = "aurora";

    private Instant createdAt = Instant.now();
    private Instant lastLoginAt;
    private long visitCount = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public long getVisitCount() { return visitCount; }
    public void setVisitCount(long visitCount) { this.visitCount = visitCount; }
}
