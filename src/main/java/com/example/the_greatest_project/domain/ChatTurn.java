package com.example.the_greatest_project.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_turn", indexes = @Index(name = "idx_chat_user", columnList = "userId"))
public class ChatTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 4000)
    private String question;

    @Lob
    private String answer;

    @Column(length = 40)
    private String engine;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
