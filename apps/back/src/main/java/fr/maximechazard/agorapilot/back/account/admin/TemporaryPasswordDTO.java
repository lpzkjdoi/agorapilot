package fr.maximechazard.agorapilot.back.account.admin;

/**
 * Réponse d'une création ou d'une réinitialisation. Le mot de passe provisoire
 * n'est donné qu'ici, une seule fois : seule son empreinte est conservée.
 */
public record TemporaryPasswordDTO(AccountDTO account, String temporaryPassword) {
}
