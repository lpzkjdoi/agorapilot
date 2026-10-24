package fr.maximechazard.agorapilot.back.account.exceptions;

/** Nouveau mot de passe refusé : règle non respectée, ou mot de passe actuel erroné. */
public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
