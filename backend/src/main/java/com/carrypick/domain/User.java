package com.carrypick.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.Mode currentMode = Enums.Mode.BUYER;

    private boolean test;

    private Instant createdAt = Instant.now();

    protected User() {}

    public User(String email, String passwordHash, String name, Enums.Mode mode, boolean test) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.currentMode = mode;
        this.test = test;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Enums.Mode getCurrentMode() { return currentMode; }
    public void setCurrentMode(Enums.Mode currentMode) { this.currentMode = currentMode; }
    public boolean isTest() { return test; }
    public Instant getCreatedAt() { return createdAt; }
}
