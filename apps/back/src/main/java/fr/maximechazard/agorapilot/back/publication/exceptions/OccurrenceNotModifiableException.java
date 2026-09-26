package fr.maximechazard.agorapilot.back.publication.exceptions;

/**
 * L'occurrence existe, mais elle est déjà servie, en cours de diffusion, ou si
 * proche de son heure que l'ordonnanceur pourrait la prendre en charge pendant
 * la modification.
 */
public class OccurrenceNotModifiableException extends RuntimeException {

    public OccurrenceNotModifiableException(String message) {
        super(message);
    }
}
