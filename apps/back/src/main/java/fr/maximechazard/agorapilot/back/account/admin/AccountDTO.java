package fr.maximechazard.agorapilot.back.account.admin;

import fr.maximechazard.agorapilot.back.account.AccountStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Un compte vu par un administrateur. Les droits sont ceux attribués, même s'ils
 * ne sont pas encore exercés (mot de passe provisoire).
 *
 * @param lockedUntil fin du verrouillage en cours, {@code null} s'il n'y en a pas
 */
public record AccountDTO(Long id, String username, AccountStatus status, List<String> permissions,
                         LocalDateTime lockedUntil, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
}
