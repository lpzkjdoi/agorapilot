package fr.maximechazard.agorapilot.back.account.exceptions;

/** Trop d'échecs de connexion depuis la même adresse IP : réponse 429. */
public class TooManyLoginAttemptsException extends RuntimeException {
    public static final String MESSAGE = "Trop de tentatives de connexion. Réessayez dans quelques minutes.";

    public TooManyLoginAttemptsException() {
        super(MESSAGE);
    }
}
