package fr.maximechazard.agorapilot.back.account.dtos;

import fr.maximechazard.agorapilot.back.account.AccountStatus;

import java.util.List;

/**
 * Le compte connecté, tel que le front l'utilise pour masquer ce qui n'est pas
 * permis.
 *
 * @param permissions les droits exercés, en codes ({@code publications:read}…) ;
 *                    vide tant que le mot de passe est provisoire
 */
public record CurrentUserDTO(String username, AccountStatus status, List<String> permissions) {
}
