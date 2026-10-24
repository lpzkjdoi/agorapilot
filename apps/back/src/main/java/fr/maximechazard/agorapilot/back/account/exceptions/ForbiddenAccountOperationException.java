package fr.maximechazard.agorapilot.back.account.exceptions;

/**
 * Action d'administration refusée par un garde-fou : sur son propre compte, ou
 * sur un droit qu'on ne détient pas.
 */
public class ForbiddenAccountOperationException extends RuntimeException {
    public ForbiddenAccountOperationException(String message) {
        super(message);
    }
}
