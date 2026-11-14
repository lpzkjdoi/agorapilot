package fr.maximechazard.agorapilot.back.notification;

/**
 * Technologie d'envoi d'une cible. Une valeur par {@link NotificationSender} :
 * ajouter un canal (e-mail, Slack…), c'est ajouter une valeur ici et
 * l'implémentation qui la prend en charge.
 */
public enum NotificationChannelType {
    /** Webhook de salon Discord : la notification devient une carte (embed). */
    DISCORD,
    /** Webhook générique : la notification est postée telle quelle, en JSON. */
    WEBHOOK
}
