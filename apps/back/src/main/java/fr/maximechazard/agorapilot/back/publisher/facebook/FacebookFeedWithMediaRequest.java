package fr.maximechazard.agorapilot.back.publisher.facebook;

import java.util.List;

/**
 * Corps de {@code POST /{page-id}/feed} pour une publication à plusieurs photos.
 * <p>
 * Record distinct de {@link FacebookFeedRequest} plutôt qu'un champ nullable :
 * Graph refuse un {@code attached_media} présent mais vide, et cette séparation
 * évite d'avoir à configurer une stratégie d'omission des nuls.
 */
public record FacebookFeedWithMediaRequest(
        String message,
        List<FacebookAttachedMedia> attached_media,
        String access_token
) {
}
