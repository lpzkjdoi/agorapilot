package fr.maximechazard.agorapilot.back.publication.exceptions;

/**
 * La livraison a bien été enregistrée (statut {@code FAILED}) mais le canal
 * distant a refusé la publication.
 */
public class DeliveryFailedException extends RuntimeException {
    public DeliveryFailedException(String message) {
        super(message);
    }
}
