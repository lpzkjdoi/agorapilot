package fr.maximechazard.agorapilot.back.notification;

/**
 * Une paire nom / valeur mise en avant (compteur, canal, motif d'erreur…).
 *
 * @param inline affichable en colonne, à côté des autres champs courts
 * @param code   valeur technique à afficher telle quelle (message d'erreur brut)
 */
public record NotificationField(String name, String value, boolean inline, boolean code) {

    public static NotificationField inline(String name, String value) {
        return new NotificationField(name, value, true, false);
    }

    public static NotificationField block(String name, String value) {
        return new NotificationField(name, value, false, false);
    }

    public static NotificationField code(String name, String value) {
        return new NotificationField(name, value, false, true);
    }
}
