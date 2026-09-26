package fr.maximechazard.agorapilot.back.publication.exceptions;

/**
 * Aucun {@code Publisher} n'est encore branché sur le canal demandé (Intramuros,
 * par exemple).
 */
public class UnsupportedDeliveryChannelException extends RuntimeException {
    public UnsupportedDeliveryChannelException(String message) {
        super(message);
    }
}
