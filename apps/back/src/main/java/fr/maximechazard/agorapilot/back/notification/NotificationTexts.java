package fr.maximechazard.agorapilot.back.notification;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;

/** Petits formats de texte partagés par les notifications métier. */
public final class NotificationTexts {

    private static final int EXCERPT_LENGTH = 80;

    private NotificationTexts() {
    }

    /**
     * Une publication n'a pas de titre : on la désigne par le début de sa
     * première ligne non vide.
     */
    public static String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "(publication sans texte)";
        }

        String firstLine = content.strip().lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("");

        if (firstLine.length() <= EXCERPT_LENGTH) {
            return firstLine;
        }

        return firstLine.substring(0, EXCERPT_LENGTH - 1).strip() + "…";
    }

    public static String channelLabel(DeliveryChannel channel) {
        return switch (channel) {
            case FACEBOOK -> "Facebook";
            case INTRAMUROS -> "Intramuros";
        };
    }

    /** « 1 diffusion », « 3 diffusions ». */
    public static String plural(long count, String singular, String plural) {
        return count + " " + (count > 1 ? plural : singular);
    }
}
