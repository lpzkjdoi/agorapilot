package fr.maximechazard.agorapilot.back.notification;

/**
 * Ce dont on notifie. Chaque cible déclare les types qu'elle reçoit
 * ({@code agorapilot.notifications.targets[].events}).
 * <p>
 * Ajouter un événement (canal suspendu, jeton Facebook expiré…) commence ici,
 * puis par la cible qui doit le recevoir.
 */
public enum NotificationType {
    /** Programme du jour, envoyé chaque matin. */
    DAILY_SUMMARY("récap quotidien"),
    /** Une occurrence n'a pas pu être diffusée sur au moins un canal. */
    DELIVERY_FAILED("échec de diffusion"),
    /** Envoi manuel, pour vérifier qu'une cible est bien reliée. */
    TEST("test");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
