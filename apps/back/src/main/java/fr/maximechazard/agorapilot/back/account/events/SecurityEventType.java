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
    PASSWORD_RESET
}
