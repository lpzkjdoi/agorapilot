package fr.maximechazard.agorapilot.back.account;

import fr.maximechazard.agorapilot.back.account.exceptions.InvalidPasswordException;

/** 12 à 128 caractères, différent du pseudo. */
public final class PasswordPolicy {
    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /** @throws InvalidPasswordException le mot de passe ne respecte pas la règle */
    public static void check(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new InvalidPasswordException("Le mot de passe doit compter de " + MIN_LENGTH + " à "
                    + MAX_LENGTH + " caractères.");
        }

        if (password.trim().equalsIgnoreCase(username)) {
            throw new InvalidPasswordException("Le mot de passe ne peut pas être le pseudo.");
        }
    }
}
