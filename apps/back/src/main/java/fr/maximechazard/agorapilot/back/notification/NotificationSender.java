package fr.maximechazard.agorapilot.back.notification;

/**
 * Envoie une notification sur une technologie donnée (Discord, webhook…).
 * <p>
 * Les implémentations sont découvertes par {@link NotificationSenderFactory} :
 * en ajouter une suffit à rendre le canal utilisable par une cible.
 */
public interface NotificationSender {

    NotificationChannelType supports();

    /**
     * @throws RuntimeException si l'envoi échoue ; {@link NotificationService}
     *                          l'isole, pour que les autres cibles soient servies
     */
    void send(NotificationTarget target, Notification notification);
}
