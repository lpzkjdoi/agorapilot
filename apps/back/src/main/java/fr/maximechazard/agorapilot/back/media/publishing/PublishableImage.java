package fr.maximechazard.agorapilot.back.media.publishing;

/**
 * Une image chargée en mémoire, prête à être envoyée à un réseau social.
 *
 * @param filename    nom présenté dans la partie multipart
 * @param contentType type après transcodage éventuel
 * @param altText     description, reprise comme légende quand le canal le permet
 * @param content     binaire complet — une affiche tient largement en mémoire,
 *                    la taille est plafonnée à l'upload
 */
public record PublishableImage(
        String filename,
        String contentType,
        String altText,
        byte[] content
) {
}
