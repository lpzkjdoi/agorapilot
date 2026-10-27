package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

/** {@code FACEBOOK_TOKEN_SOURCE=legacy} : la connexion depuis l'interface est coupée. */
public class FacebookConnectionDisabledException extends RuntimeException {
    public FacebookConnectionDisabledException() {
        super("La connexion depuis AgoraPilot est désactivée (FACEBOOK_TOKEN_SOURCE=legacy) : "
                + "le jeton historique, posé par le bootstrap, publie seul.");
    }
}
