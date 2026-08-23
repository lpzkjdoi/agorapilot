package fr.maximechazard.agorapilot.back.publisher.facebook;

/**
 * Corps de {@code POST /{page-id}/feed} pour une publication sans visuel.
 * <p>
 * Les composants portent les noms de l'API Graph, {@code snake_case} compris :
 * c'est ce qui évite d'avoir à annoter le record, alors que le projet mêle le
 * databind de Jackson 3 et les annotations de Jackson 2.
 */
public record FacebookFeedRequest(
        String message,
        String access_token
) {
}
