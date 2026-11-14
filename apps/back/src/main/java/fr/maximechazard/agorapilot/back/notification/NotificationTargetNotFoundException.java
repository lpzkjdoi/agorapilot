package fr.maximechazard.agorapilot.back.notification;

/** Aucune cible configurée sous ce nom (inconnue, ou sans URL dans cet environnement). */
public class NotificationTargetNotFoundException extends RuntimeException {
    public NotificationTargetNotFoundException(String name) {
        super("No configured notification target named '" + name + "'.");
    }
}
