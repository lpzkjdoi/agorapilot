package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

/** Une connexion refusée, avec un motif déjà formulé pour l'utilisateur. */
public class FacebookConnectionException extends RuntimeException {
    public FacebookConnectionException(String message) {
        super(message);
    }
}
