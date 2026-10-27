package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Une demande de connexion en cours, identifiée par le {@code state} OAuth.
 * <p>
 * Le retour de Facebook est une navigation venue d'un autre site : le cookie de
 * session, en {@code SameSite=strict}, n'y est pas joint. Cette ligne tient lieu
 * de session — elle dit quel compte a lancé la connexion — et de protection
 * CSRF : un retour sans {@code state} connu est refusé. Supprimée dès sa lecture.
 */
@Entity
@Table(name = "facebook_oauth_states")
@Getter
@NoArgsConstructor
public class FacebookOAuthState {

    @Id
    @Column(length = 64)
    private String state;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public FacebookOAuthState(String state, String username, LocalDateTime createdAt) {
        this.state = state;
        this.username = username;
        this.createdAt = createdAt;
    }
}
