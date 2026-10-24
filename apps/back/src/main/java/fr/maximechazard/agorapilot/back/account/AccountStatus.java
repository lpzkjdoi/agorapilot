package fr.maximechazard.agorapilot.back.account;

/**
 * {@code PENDING_PASSWORD} : mot de passe provisoire, donné par un administrateur.
 * La personne peut se connecter, mais sa seule action possible est de le changer :
 * le compte n'exerce aucun de ses droits d'ici là.
 */
public enum AccountStatus {
    PENDING_PASSWORD,
    ACTIVE,
    DISABLED
}
