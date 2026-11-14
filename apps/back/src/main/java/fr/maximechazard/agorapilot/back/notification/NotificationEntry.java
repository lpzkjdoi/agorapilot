package fr.maximechazard.agorapilot.back.notification;

import java.time.Instant;

/**
 * Une ligne d'une notification — typiquement une diffusion du programme du jour.
 * <p>
 * Texte brut, sans mise en forme : c'est au canal de décider comment afficher
 * l'heure (Discord l'affiche dans le fuseau du lecteur) ou le lien.
 *
 * @param at     instant concerné, facultatif
 * @param label  intitulé principal
 * @param detail précision secondaire (canaux, campagne…), facultative
 * @param link   lien vers l'application, facultatif
 */
public record NotificationEntry(Instant at, String label, String detail, String link) {
}
