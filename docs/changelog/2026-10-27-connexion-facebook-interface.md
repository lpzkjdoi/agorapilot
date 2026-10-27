# Connexion du canal Facebook depuis l'interface

## Contexte

Le jeton de Page s'obtenait à la main : un jeton court généré dans le Graph
Explorer, puis `POST /admin/facebook/bootstrap` en `curl` sur la boucle locale du
VPS, avec session et en-tête XSRF. Le renouvellement nocturne
(`FacebookTokenService.renewIfNeeded()`) ne se déclenche jamais, car le jeton de
Page est permanent. Son chemin de secours repose en plus sur un jeton
utilisateur expiré au bout d'environ 60 jours. Enfin, un jeton invalidé (mot de
passe changé, rôle perdu sur la Page) ne se découvrait qu'à l'échec d'une
diffusion. Cf. l'analyse du [jalon v1.0](./2026-10-27-jalon-v1.md).

Contrainte posée : **garder l'ancien code intact** et construire le nouveau à
côté, pour pouvoir revenir en arrière facilement. Le nettoyage (suppression du
bootstrap et du renouvellement) fera l'objet d'une PR séparée, une fois la
connexion validée.

## Changements

### Back

- **Paquet à part `publisher/facebook/connection`**, sans modifier
  `FacebookTokenService`, `FacebookTokenScheduler` ni `FacebookAdminController` :
  - `FacebookOAuthClient` : échange du code, jeton longue durée, `/me/accounts`,
    `debug_token` ;
  - `FacebookConnectionService` : demande de connexion (`state` à usage unique),
    retour de Facebook, contrôle du jeton ;
  - `FacebookConnectionController` sous `/api/channels/facebook` :
    - `GET /connection` et `POST /connection` (ouvre une demande et rend l'adresse
      de la boîte de dialogue) ;
    - `POST /connection/check` ;
    - `GET /callback`, public ;
  - `FacebookConnectionHealthCheck` : contrôle quotidien à 7 h.
- **Migration `V5__connexion_facebook.sql`** : tables `facebook_connections` et
  `facebook_oauth_states`, type de journal `FACEBOOK_CONNECTED`. Elle ne fait
  qu'ajouter, et `facebook_tokens` n'est pas touchée.
- **Interrupteur `FACEBOOK_TOKEN_SOURCE`** (`connection` par défaut, `legacy`)
  lu par `FacebookPageTokenProvider`, désormais seul fournisseur du jeton de
  `FacebookPublisher` :
  - en `connection`, le jeton connecté publie, et à défaut le jeton historique :
    la bascule ne coupe rien ;
  - en `legacy`, seul le jeton historique publie ; connexion et contrôle sont
    coupés.
- **Retour de Facebook public** : le cookie de session est en `SameSite=strict`
  et n'accompagne pas une navigation venue de facebook.com. C'est le `state`
  OAuth (10 minutes, usage unique, stocké en base avec le compte demandeur) qui
  identifie la demande et protège contre le CSRF.
- **Une connexion réussie lève la suspension Facebook** et s'inscrit au journal
  au nom de la personne qui a cliqué.
- **Le contrôle suspend le canal** quand le jeton est invalide ou n'a plus
  `pages_manage_posts`. Une indisponibilité de Facebook ne suspend rien
  (« Indéterminé »). Le résultat est gardé en base.
- L'échéance est nullable : `null` veut dire jeton permanent, au lieu de la
  fausse date à dix ans de l'ancien code.

### Front

- **Page « Canal Facebook »** (`/canal-facebook`, `channels:manage`) :
  - jeton qui publie (connecté, historique ou aucun) ;
  - Page connectée, auteur et date de connexion, échéances, permissions ;
  - dernier contrôle ;
  - boutons « Connecter / Reconnecter la Page » et « Contrôler maintenant » ;
  - en mode historique, un message remplace les boutons.
- Entrée « Canal Facebook » dans la barre de navigation.
- Lien « Reconnecter la Page » dans le bandeau de suspension Facebook.
- Libellé « Page Facebook connectée » dans le journal de sécurité.

### Déploiement et documentation

- `FACEBOOK_TOKEN_SOURCE` est propagée par `docker-compose.preprod.yaml`,
  `.env.preprod.example` et `deploy-preprod.yml` (variable facultative de
  l'Environment `preprod`).
- Runbook : nouvelle section « Connexion du canal Facebook ». Elle couvre le
  prérequis Meta (URI de redirection), le contrôle quotidien, la bascule et les
  deux niveaux de retour arrière (variable, ou revert de la PR).

## Vérification

- Back : `mvn -B verify` (tests unitaires du service, du client Graph et du
  fournisseur de jeton, test du contrôleur, persistance de V5 sur H2, parcours
  complet avec vraie session et retour sans session, table des droits).
- Front : `npm test`, `npm run lint`, build de production.
- **Pas encore vérifié en conditions réelles** :
  - la connexion contre la vraie app Meta en préproduction, qui demande d'abord
    de déclarer l'URI de redirection ;
  - le comportement de `debug_token` sur la vraie Page.

## À suivre

- PR de nettoyage, une fois la connexion validée en préproduction : supprimer
  `POST /admin/facebook/bootstrap`, `renewIfNeeded()`, `FacebookTokenScheduler`,
  la table `facebook_tokens`, le repli sur le jeton historique et l'interrupteur.
- Option : saisie d'un jeton de System User du Business Manager.

## Commit

_voir PR_
