package fr.maximechazard.agorapilot.back.account.events;

public enum SecurityEventType {
    LOGIN_SUCCEEDED,
    /** Le pseudo visé peut ne correspondre à aucun compte. */
    LOGIN_FAILED,
    ACCOUNT_LOCKED,
    /** Par la personne elle-même. */
    PASSWORD_CHANGED,
    ACCOUNT_CREATED,
    PERMISSIONS_CHANGED,
    ACCOUNT_DISABLED,
    /** Par un administrateur : nouveau mot de passe provisoire, compte (ré)activé. */
    PASSWORD_RESET,

    // Actions qui ne laissent pas de ligne derrière elles : le journal en garde la trace.
    PUBLICATION_DELETED,
    OCCURRENCE_RESCHEDULED,
    OCCURRENCE_RETRIED,
    OCCURRENCE_CANCELLED,
    /** Par l'ordonnanceur, sans acteur, après un refus bloquant du canal. */
    CHANNEL_SUSPENDED,
    CHANNEL_RESUMED,
    /** Page Facebook (re)connectée depuis l'interface, par le compte qui l'a demandé. */
    FACEBOOK_CONNECTED
}
