package fr.maximechazard.agorapilot.back.account.admin;

import java.security.SecureRandom;

/**
 * Mots de passe provisoires : 16 caractères tirés au hasard, en quatre groupes
 * ({@code k7mq-2xpr-…}), dans un alphabet sans caractères qu'on confond à la
 * lecture ({@code 0/o}, {@code 1/l/i}). Environ 80 bits d'entropie.
 */
final class TemporaryPasswords {
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int GROUPS = 4;
    private static final int GROUP_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    static String generate() {
        StringBuilder password = new StringBuilder();

        for (int group = 0; group < GROUPS; group++) {
            if (group > 0) {
                password.append('-');
            }
            for (int i = 0; i < GROUP_LENGTH; i++) {
                password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
        }

        return password.toString();
    }
}
