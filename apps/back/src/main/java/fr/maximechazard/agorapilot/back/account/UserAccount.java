package fr.maximechazard.agorapilot.back.account;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Compte applicatif : une personne = un compte. Jamais supprimé, seulement
 * désactivé, pour garder la trace de ce qu'il a fait.
 * <p>
 * Pas de {@code @Data} : un {@code toString} généré écrirait l'empreinte du mot
 * de passe dans les journaux au premier log d'un compte.
 */
@Entity
@Table(name = "user_accounts")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@Getter
@Setter
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** En minuscules : l'unicité est insensible à la casse. */
    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    /** Chargés avec le compte : ils sont relus à chaque requête authentifiée. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    /** Échecs de connexion consécutifs, remis à zéro au succès ou au verrouillage. */
    @Column(nullable = false)
    private int failedAttempts = 0;

    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;

    /**
     * Une session ouverte avant cette date n'est plus valable : changer ou
     * réinitialiser un mot de passe déconnecte toutes les autres sessions.
     */
    @Column(nullable = false)
    private LocalDateTime passwordChangedAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public UserAccount(String username, String passwordHash, AccountStatus status, Set<Permission> permissions,
                       LocalDateTime passwordChangedAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.status = status;
        this.permissions = permissions.isEmpty() ? new HashSet<>() : EnumSet.copyOf(permissions);
        this.passwordChangedAt = passwordChangedAt;
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Les droits que le compte exerce effectivement : aucun tant que son mot de
     * passe est provisoire, aucun une fois désactivé.
     */
    public Set<Permission> effectivePermissions() {
        return status == AccountStatus.ACTIVE ? Set.copyOf(permissions) : Set.of();
    }
}
