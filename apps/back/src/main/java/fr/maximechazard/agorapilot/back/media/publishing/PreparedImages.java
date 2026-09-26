package fr.maximechazard.agorapilot.back.media.publishing;

import java.util.List;

/**
 * Résultat de la préparation des visuels d'une publication.
 *
 * @param images  les images retenues, dans l'ordre
 * @param skipped les libellés des médias écartés, avec la raison — un PDF n'est
 *                pas publiable, et les canaux plafonnent le nombre de photos.
 *                Remonté plutôt que tu : une diffusion amputée en silence est
 *                pire qu'un échec franc.
 */
public record PreparedImages(List<PublishableImage> images, List<String> skipped) {

    public boolean isEmpty() {
        return images.isEmpty();
    }
}
