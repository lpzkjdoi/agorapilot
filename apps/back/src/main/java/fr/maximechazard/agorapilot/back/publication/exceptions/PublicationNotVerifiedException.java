package fr.maximechazard.agorapilot.back.publication.exceptions;

/** Seule une publication vérifiée se programme ou se diffuse. */
public class PublicationNotVerifiedException extends RuntimeException {
    public PublicationNotVerifiedException(Long publicationId) {
        super("La publication " + publicationId + " n'est pas vérifiée : elle ne peut être ni programmée ni diffusée.");
    }
}
