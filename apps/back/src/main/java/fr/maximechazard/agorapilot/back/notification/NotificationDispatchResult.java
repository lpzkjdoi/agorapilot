package fr.maximechazard.agorapilot.back.notification;

/**
 * Issue de l'envoi d'une notification à une cible.
 *
 * @param error motif de l'échec, URL du webhook masquée ; {@code null} en cas de succès
 */
public record NotificationDispatchResult(String target, boolean delivered, String error) {

    static NotificationDispatchResult delivered(String target) {
        return new NotificationDispatchResult(target, true, null);
    }

    static NotificationDispatchResult failed(String target, String error) {
        return new NotificationDispatchResult(target, false, error);
    }
}
