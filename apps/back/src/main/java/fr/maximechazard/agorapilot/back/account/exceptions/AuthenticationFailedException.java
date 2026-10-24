package fr.maximechazard.agorapilot.back.account.exceptions;

/**
 * Connexion refusée. Un seul message pour toutes les causes (pseudo inconnu,
 * mauvais mot de passe, compte désactivé ou verrouillé) : la réponse ne dit pas
 * quels comptes existent.
 */
public class AuthenticationFailedException extends RuntimeException {
    public static final String MESSAGE = "Identifiant ou mot de passe incorrect.";

    public AuthenticationFailedException() {
        super(MESSAGE);
    }
}
