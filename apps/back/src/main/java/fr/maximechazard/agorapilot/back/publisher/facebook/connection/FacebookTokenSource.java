package fr.maximechazard.agorapilot.back.publisher.facebook.connection;

/**
 * D'où vient le jeton de Page qui sert à publier ({@code FACEBOOK_TOKEN_SOURCE}).
 * <p>
 * L'interrupteur de retour arrière de la connexion depuis l'interface : les deux
 * mécanismes cohabitent le temps de valider le nouveau, et repasser à l'ancien ne
 * demande qu'une variable d'environnement et un redéploiement.
 */
public enum FacebookTokenSource {
    /**
     * Le jeton de la connexion faite depuis l'interface. Tant qu'aucune Page n'a
     * été connectée, le jeton historique sert encore : la bascule n'interrompt
     * aucune diffusion.
     */
    CONNECTION,
    /**
     * Le mécanisme historique seul ({@code POST /admin/facebook/bootstrap},
     * table {@code facebook_tokens}) : connexion depuis l'interface désactivée,
     * contrôle quotidien coupé.
     */
    LEGACY
}
