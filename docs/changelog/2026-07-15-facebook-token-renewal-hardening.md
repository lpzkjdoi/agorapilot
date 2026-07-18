# Durcissement du renouvellement du token Facebook

- **Date** : 2026-07-15
- **Commit** : [`30b31d4`](https://github.com/lpzkjdoi/agorapilot/commit/30b31d4c4850f1a1124010c04415317e3ec4d3b0)
- **Branche** : `develop`
- **Périmètre** : `apps/back` (`publisher/facebook`)

## Contexte

Revue du système de gestion et de renouvellement du token Facebook (contournement nécessaire en l'absence de possibilité de créer un utilisateur/System User dédié côté Meta). Plusieurs faiblesses ont été identifiées puis corrigées. L'authentification des endpoints admin reste hors périmètre — décision non tranchée entre accès VPN et authentification classique.

## Changements

1. **Renouvellement corrigé** — le token utilisateur longue durée est désormais persisté séparément du token de Page (`FacebookToken.tokenType` = `"user"` / `"page"`). Le renouvellement rafraîchit ce token utilisateur (l'usage documenté par l'API Graph pour `fb_exchange_token`) puis re-dérive un token de Page frais, au lieu de ré-échanger directement le token de Page comme précédemment (comportement non garanti par l'API).
2. **Observabilité** — `FacebookTokenScheduler` garde en mémoire l'heure du dernier contrôle et la dernière erreur de renouvellement.
3. **Endpoint de statut enrichi** — `GET /admin/facebook/token/status` renvoie désormais, pour le token page et le token user : aperçu, date d'expiration, jours restants, plus l'état du dernier renouvellement (heure, erreur éventuelle).
4. **Réponses Graph API typées** — remplacement du parsing `Map<?,?>` par des records dédiés (`TokenExchangeResponse`, `PageTokenResponse`, `DebugTokenResponse`), cohérent avec le style déjà utilisé par `FacebookPostRequest`/`FacebookPostResponse`.
5. **Timeouts HTTP** — 5s connexion / 10s lecture sur les appels vers l'API Graph (absents auparavant).
6. **Bug corrigé : version d'API manquante** — `FacebookTokenService` n'incluait pas du tout la version d'API dans ses appels (contrairement à `FacebookClient`). Corrigé, avec `v25.0` (version actuelle communiquée) comme valeur par défaut dans `application.yaml`.
7. **Client HTTP unifié** — un seul `RestClient` partagé (au lieu de `RestTemplate` + `RestClient` séparés), avec timeouts centralisés.
8. **10 tests unitaires ajoutés** sur `FacebookTokenService` (seuil de renouvellement, token page absent, token user absent, cas token permanent, bootstrap, statut).

## Vérification

`mvn clean test` : 11 tests passent (10 nouveaux + le test de contexte Spring existant, qui valide au passage tout le câblage des beans dont le `RestClient` désormais partagé entre `FacebookClient` et `FacebookTokenService`). Aucune régression détectée.

## Hors périmètre (pour rappel)

- Authentification des endpoints `/admin/facebook/*`.
- Alerting push (email/Slack) en cas d'échec de renouvellement — nécessiterait de choisir un canal de notification ; seule l'observabilité (dernier check/erreur consultable via l'API) a été mise en place.
