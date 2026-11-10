-- Fin du mécanisme historique du jeton Facebook (bootstrap à la main et
-- renouvellement nocturne) : la connexion depuis l'interface, validée en
-- préproduction, est désormais la seule source du jeton qui publie
-- (`facebook_connections`, cf. V5).
--
-- Irréversible : le jeton utilisateur et le jeton de Page historiques
-- disparaissent. Un retour arrière vers une version antérieure demanderait de
-- refaire le bootstrap.

DROP TABLE facebook_tokens;
