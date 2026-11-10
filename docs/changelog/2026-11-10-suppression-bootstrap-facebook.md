# Supprimer le bootstrap et le renouvellement historiques du jeton Facebook

## Contexte

La connexion de la Page depuis la page « Canal Facebook » (cf.
[changelog du 2026-10-27](./2026-10-27-connexion-facebook-interface.md)) a été
construite **à côté** de l'ancien mécanisme, avec un interrupteur de retour
arrière, le temps de la valider. C'est fait en préproduction le 2026-11-09. Il
restait donc à supprimer l'ancien mécanisme :

- le bootstrap à la main (Graph Explorer, puis `curl` sur la boucle locale du VPS) ;
- un renouvellement nocturne qui ne se déclenche jamais pour un jeton de Page
  permanent, et dont le chemin de secours reposait sur un jeton utilisateur
  expirant au bout d'environ 60 jours.

## Changements

### Back

- **Supprimés** : `FacebookAdminController` (`POST /admin/facebook/bootstrap`,
  `GET /admin/facebook/token/status`) et son `FacebookAdminExceptionHandler`,
  `FacebookTokenService` (dont `renewIfNeeded()`), `FacebookTokenScheduler`,
  l'entité et le dépôt `FacebookToken`, ainsi que l'interrupteur
  `FACEBOOK_TOKEN_SOURCE` (`FacebookTokenSource`, `FacebookConnectionDisabledException`).
- **`FacebookPageTokenProvider`** ne lit plus que `facebook_connections`. Sans
  Page connectée, la diffusion échoue avec le motif « Aucune Page Facebook
  connectée : la connecter depuis la page « Canal Facebook » ».
- **`FacebookConnectionService`** : plus de garde « mode historique » sur la
  connexion, le retour de Facebook et le contrôle quotidien.
- **`GET /api/channels/facebook/connection`** ne renvoie plus que `connection`
  (`source` et `tokenInUse` n'ont plus de sens).
- **Migration `V6__suppression_jetons_historiques.sql`** : `DROP TABLE facebook_tokens`.
  Irréversible : revenir à une version antérieure demanderait de refaire le bootstrap.
- `/admin/**` reste réservé à `channels:manage` et hors du proxy nginx : il ne
  sert plus qu'à `/admin/scheduler/status`.

### Front

- Page « Canal Facebook » : plus de bandeau « mode historique » ni de carte
  « Jeton utilisé pour publier ». Sans connexion, une carte annonce que rien ne
  peut être publié. Les boutons de connexion sont toujours proposés.

### Déploiement et documentation

- `FACEBOOK_TOKEN_SOURCE` retirée de `docker-compose.preprod.yaml`,
  `.env.preprod.example` et `deploy-preprod.yml`. La variable peut être
  supprimée de l'Environment GitHub `preprod` si elle y a été définie ; elle
  est désormais ignorée.
- Commentaires mis à jour : `Dockerfile` (raison de `TZ`), `nginx.conf`,
  compose.
- Runbook : sections « Bascule et retour arrière » et « Endpoints
  d'administration Facebook » remplacées par « Sans Page connectée » ; remède
  du code 190 = reconnecter la Page ; dette « validité du token jamais
  vérifiée » retirée (comblée par le contrôle quotidien).
- Roadmap : connexion du canal Facebook cochée (reste l'option System User).

## Vérification

- Back : `mvn -B clean verify`.
- Front : `npm test`, `npm run lint`, build de production.
- La préproduction a déjà une Page connectée : la `V6` n'y supprime que la table
  historique, et la diffusion continue avec le jeton connecté.

## Commit

_voir PR_
