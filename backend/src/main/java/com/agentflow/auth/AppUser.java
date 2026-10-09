package com.agentflow.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AppUser {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String username;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 30) private String role;
    @Column(nullable = false) private Instant createdAt;

    protected AppUser() {}
    public AppUser(String username, String passwordHash) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = "USER";
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
}
