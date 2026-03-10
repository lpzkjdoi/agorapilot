package fr.maximechazard.agorapilot.back.publisher.facebook;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "facebook_tokens")
public class FacebookToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String accessToken;

    @Column(nullable = false)
    private String tokenType;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
