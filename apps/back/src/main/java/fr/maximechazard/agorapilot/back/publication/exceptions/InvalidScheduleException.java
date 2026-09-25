package fr.maximechazard.agorapilot.back.publication.exceptions;

/**
 * La date ou l'heure demandée ne peut pas accueillir de diffusion : jour passé,
 * fenêtre de publication du jour déjà close, heure trop proche.
 */
public class InvalidScheduleException extends RuntimeException {

    public InvalidScheduleException(String message) {
        super(message);
    }
}
