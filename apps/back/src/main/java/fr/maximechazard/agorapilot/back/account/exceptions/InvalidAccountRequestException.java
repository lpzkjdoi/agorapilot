package fr.maximechazard.agorapilot.back.account.exceptions;

/** Pseudo hors règle, déjà pris, ou droit inconnu. */
public class InvalidAccountRequestException extends RuntimeException {
    private final boolean conflict;

    public InvalidAccountRequestException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    /** Pseudo déjà pris : un 409 plutôt qu'un 400. */
    public boolean isConflict() {
        return conflict;
    }
}
