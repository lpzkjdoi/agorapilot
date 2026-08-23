package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.media.publishing.PublishableImage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Appels de publication à l'API Graph.
 * <p>
 * Trois chemins, selon le nombre de visuels :
 * <ul>
 *   <li>aucun : {@code POST /{page-id}/feed} en JSON ;</li>
 *   <li>un seul : {@code POST /{page-id}/photos}, qui publie directement ;</li>
 *   <li>plusieurs : autant de dépôts non publiés que de photos, puis un post au
 *       fil qui les rattache par leur {@code media_fbid}.</li>
 * </ul>
 * Les binaires sont <strong>envoyés</strong> en multipart, jamais désignés par une
 * URL : les serveurs de Facebook ne peuvent pas atteindre nos environnements, dont
 * l'accès est restreint au réseau WireGuard par Traefik.
 */
@Service
@RequiredArgsConstructor
public class FacebookClient {

    /** Limite de l'API Graph pour les photos rattachées à un post au fil. */
    public static final int MAX_PHOTOS_PER_POST = 10;

    private final RestClient restClient;
    private final FacebookProperties props;
    private final ObjectMapper mapper;

    public FacebookPostResponse publish(String message, List<PublishableImage> images, String token) {
        if (images.isEmpty()) {
            return postToFeed(message, token);
        }

        if (images.size() == 1) {
            return postSinglePhoto(message, images.getFirst(), token);
        }

        List<FacebookAttachedMedia> attachments = images.stream()
                .map(image -> new FacebookAttachedMedia(uploadUnpublishedPhoto(image, token).id()))
                .toList();

        return postToFeedWithMedia(message, attachments, token);
    }

    // ------------------------------ Sans visuel -----------------------------

    private FacebookPostResponse postToFeed(String message, String token) {
        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(props.pageId(), "feed")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FacebookFeedRequest(message, token))
                .retrieve()
                .body(String.class);

        return mapper.readValue(response, FacebookPostResponse.class);
    }

    // ------------------------------ Une photo -------------------------------

    /**
     * Publie directement : avec une seule photo, un dépôt non publié suivi d'un
     * post au fil produirait un rendu différent et un appel de plus pour rien.
     */
    private FacebookPostResponse postSinglePhoto(String message, PublishableImage image, String token) {
        MultiValueMap<String, Object> parts = photoParts(image, token);
        parts.add("message", message);

        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(props.pageId(), "photos")
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(String.class);

        return mapper.readValue(response, FacebookPostResponse.class);
    }

    // --------------------------- Plusieurs photos ---------------------------

    private FacebookPostResponse uploadUnpublishedPhoto(PublishableImage image, String token) {
        MultiValueMap<String, Object> parts = photoParts(image, token);
        parts.add("published", "false");

        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(props.pageId(), "photos")
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(String.class);

        return mapper.readValue(response, FacebookPostResponse.class);
    }

    private FacebookPostResponse postToFeedWithMedia(
            String message, List<FacebookAttachedMedia> attachments, String token) {

        String response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(props.pageId(), "feed")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FacebookFeedWithMediaRequest(message, attachments, token))
                .retrieve()
                .body(String.class);

        return mapper.readValue(response, FacebookPostResponse.class);
    }

    // -------------------------------- Commun --------------------------------

    private MultiValueMap<String, Object> photoParts(PublishableImage image, String token) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // `source` est le paramètre binaire de l'API Photos. La ressource doit
        // porter un nom de fichier, sans quoi Spring l'envoie comme une partie
        // texte et Graph refuse la requête.
        parts.add("source", new ByteArrayResource(image.content()) {
            @Override
            public String getFilename() {
                return image.filename();
            }
        });
        parts.add("access_token", token);

        if (StringUtils.hasText(image.altText())) {
            parts.add("alt_text_custom", image.altText().trim());
        }

        return parts;
    }
}
