package fr.maximechazard.agorapilot.back.account.exceptions;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Compte " + id + " introuvable.");
    }
}
