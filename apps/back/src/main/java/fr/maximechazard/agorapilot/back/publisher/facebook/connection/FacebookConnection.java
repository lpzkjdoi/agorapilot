package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * La Page connectée depuis l'interface et son jeton. Une ligne au plus :
 * reconnecter remplace la précédente.
 * <p>
 * Le jeton de Page obtenu à partir d'un jeton utilisateur longue durée n'expire
 * pas ; ce qui l'invalide (mot de passe changé, rôle perdu sur la Page,
 * autorisation retirée) ne se voit qu'en le contrôlant, d'où le résultat du
 * dernier contrôle conservé ici.
 */
@Entity
@Table(name = "facebook_connections")
@Data
@NoArgsConstructor
public class FacebookConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String pageId;

    @Column(nullable = false)
    private String pageName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String accessToken;

    /** Permissions accordées, séparées par des virgules. */
    @Column(nullable = false, length = 1024)
    private String scopes;

    @Column(nullable = false, length = 32)
    private String connectedBy;

    @Column(nullable = false)
    private LocalDateTime connectedAt;

    /** {@code null} : jeton permanent. */
    private LocalDateTime expiresAt;

    /** Fin de l'accès aux données accordé par l'utilisateur, si Facebook en annonce une. */
    private LocalDateTime dataAccessExpiresAt;

    private LocalDateTime lastCheckedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CheckResult lastCheckResult;

    @Column(length = 512)
    private String lastCheckMessage;

    public enum CheckResult {
        VALID,
        /** Facebook dit le jeton inutilisable : le canal est suspendu. */
        INVALID,
        /** Contrôle impossible (réseau, Facebook indisponible) : rien n'est conclu. */
        UNKNOWN
    }
}
