package fr.maximechazard.agorapilot.back.account;

import java.util.Locale;
import java.util.regex.Pattern;

/** Règles du pseudo : 3 à 32 caractères parmi {@code a-z 0-9 . _ -}, insensible à la casse. */
public final class Usernames {
    private static final Pattern VALID = Pattern.compile("[a-z0-9._-]{3,32}");

    private Usernames() {
    }

    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return VALID.matcher(normalized).matches();
    }
}
