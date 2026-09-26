package fr.maximechazard.agorapilot.back.publisher.facebook;

/**
 * Référence d'une photo déposée sans être publiée, à rattacher à un post.
 *
 * @param media_fbid identifiant renvoyé par {@code POST /{page-id}/photos?published=false}
 */
public record FacebookAttachedMedia(String media_fbid) {
}
