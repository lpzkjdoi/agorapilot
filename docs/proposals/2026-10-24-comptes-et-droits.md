# Comptes applicatifs et droits

- **Date** : 2026-10-24
- **Statut** : conception validée ; étapes 1 (Flyway) et 2 (socle d'authentification) livrées
- **Périmètre** : `apps/back` (sécurité, comptes, migrations), `apps/front` (connexion, garde, administration)

## Contexte

Toute l'API est ouverte (`permitAll`) : l'accès ne repose que sur le réseau, la
préprod étant derrière WireGuard. Rien ne distingue qui programme, diffuse ou
supprime.

## Décisions

Prises le 2026-10-24 :

1. **Comptes applicatifs** : une personne = un compte. Un compte a un pseudo, un
   mot de passe, un statut et des droits.
2. **Au maximum restrictif** : tout est refusé par défaut, y compris la lecture.
3. **Droits fins + profils** : des droits par ressource et par action, stockés
   un par un. Des profils prédéfinis servent seulement à pré-cocher les cases,
   comme les rôles d'un dépôt GitHub.
4. **Mot de passe provisoire donné par l'administrateur** : pas d'inscription
   libre ni d'e-mail. L'administrateur crée le compte ou réinitialise le mot de
   passe, et la personne doit le changer à sa première connexion.
5. **Flyway d'abord** : les tables des comptes arrivent par migration
   versionnée, pas par `ddl-auto: update`.

## Principes

- **Refus par défaut.** La chaîne de filtres se termine par `denyAll()`. Seuls
  l'écran de connexion et la vérification de santé sont publics. Un endpoint
  oublié est donc fermé, jamais ouvert.
- **Le back fait foi.** Chaque endpoint exige son droit (`@PreAuthorize`).
  Le front masque les actions non permises par confort, pas par sécurité.
- **Droits relus à chaque requête.** Statut et droits sont rechargés depuis la
  base à chaque appel : un compte désactivé ou un droit retiré prend effet
  immédiatement, sans attendre l'expiration de la session.
- **Moindre privilège.** Un compte créé n'a aucun droit tant que
  l'administrateur ne lui en donne pas.

## Le compte

| Champ | Détail |
|---|---|
| `username` | Pseudo unique, insensible à la casse, 3 à 32 caractères `[a-z0-9._-]`. |
| `passwordHash` | BCrypt, coût 12. Le mot de passe en clair n'est jamais stocké ni journalisé. |
| `status` | `PENDING_PASSWORD` : mot de passe provisoire, la seule action possible est de le changer. `ACTIVE`. `DISABLED` : connexion refusée, sessions coupées. |
| `permissions` | Ensemble de droits (table `user_permissions`). |
| `failedAttempts`, `lockedUntil` | Verrouillage temporaire après 5 échecs : 15 minutes. |
| `createdAt`, `lastLoginAt`, `passwordChangedAt` | Suivi. |

Pas de suppression d'un compte : on le désactive, pour garder la traçabilité de
ce qu'il a fait.

## Les droits

Notation `ressource:action`, comme les scopes GitHub.

| Droit | Autorise | Endpoints |
|---|---|---|
| `publications:read` | Voir les publications, le calendrier, le tableau de bord, les suspensions de canal | `GET /api/publications`, `GET /api/occurrences/**`, `GET /api/dashboard`, `GET /api/channels/suspensions` |
| `publications:write` | Créer et modifier une publication (en brouillon), ses visuels, sa campagne | `POST /api/publications`, `PUT /api/publications/{id}`, `PUT …/medias`, `PUT …/campaign` |
| `publications:verify` | Passer une publication en `VERIFIED`, ou la repasser en brouillon | `status` dans `POST` / `PUT /api/publications` |
| `publications:delete` | Supprimer une publication | `DELETE /api/publications/{id}` |
| `occurrences:schedule` | Programmer, déplacer, annuler une diffusion, reprendre un échec | `POST /api/occurrences`, `PUT …/schedule`, `DELETE /api/occurrences/{id}`, `POST …/retry` |
| `publications:publish` | Diffuser immédiatement | `POST /api/publications/{id}/deliveries` |
| `campaigns:read` | Voir les campagnes | `GET /api/campaigns` |
| `campaigns:write` | Créer, clôturer une campagne | `POST /api/campaigns`, `POST …/closure` |
| `medias:read` | Voir la médiathèque et les fichiers | `GET /api/medias/**` |
| `medias:write` | Téléverser, renommer, archiver un média | `POST /api/medias`, `PATCH /api/medias/{id}` |
| `medias:delete` | Supprimer un média | `DELETE /api/medias/{id}` |
| `channels:manage` | Lever une suspension, jeton Facebook, état de l'ordonnanceur | `DELETE /api/channels/{channel}/suspension`, `/admin/**` |
| `users:manage` | Administrer les comptes | `/api/users/**` |

Deux règles métier renforcent `publications:verify` :

- **Seule une publication `VERIFIED` peut être programmée ou diffusée.** Le back
  refuse sinon (`409`). C'est aussi le garde-fou prévu par la
  [proposition IA](./2026-09-24-ai-features-and-email-intake.md) : l'IA ne crée
  que des brouillons.
- **Modifier le texte d'une publication `VERIFIED` la repasse en brouillon**,
  sauf pour qui détient `publications:verify`. Sans cette règle, un rédacteur
  pourrait réécrire un texte déjà validé et programmé.

Swagger (`/swagger-ui`, `/v3/api-docs`) n'est servi qu'en profil `dev`.

## Les profils

Ils ne sont pas stockés : choisir un profil coche les droits correspondants,
que l'administrateur peut ensuite ajuster un par un.

| Profil | Droits |
|---|---|
| Lecteur | `publications:read`, `campaigns:read`, `medias:read` |
| Rédacteur | Lecteur + `publications:write`, `campaigns:write`, `medias:write` |
| Diffuseur | Rédacteur + `publications:verify`, `occurrences:schedule`, `publications:publish`, `publications:delete`, `medias:delete` |
| Administrateur | Tous les droits, dont `channels:manage` et `users:manage` |

## Authentification

- **Session côté serveur**, pas de JWT : une session se révoque immédiatement.
  Cookie `HttpOnly`, `Secure`, `SameSite=Strict`. Expiration après 30 minutes
  d'inactivité, et au bout de 12 heures dans tous les cas.
- **CSRF activé** : jeton dans un cookie `XSRF-TOKEN`, renvoyé en en-tête par le
  `HttpClient` d'Angular (`withXsrfConfiguration`).
- **Session régénérée à la connexion**, contre la fixation de session.
- **Message d'échec unique** (« Identifiant ou mot de passe incorrect ») et
  temps de réponse constant, que le pseudo existe ou non : on ne révèle pas
  quels comptes existent.
- **Mot de passe** : 12 à 128 caractères, différent du pseudo et du mot de passe
  provisoire. Le mot de passe provisoire est généré par le serveur et affiché
  une seule fois à l'administrateur.
- **Désactivation, réinitialisation ou changement de droits** : les sessions du
  compte sont invalidées.

Endpoints :

- `POST /api/auth/login`, `POST /api/auth/logout` ;
- `GET /api/me` : pseudo, statut, droits ; c'est ce que le front utilise pour
  masquer les actions ;
- `PUT /api/me/password` : seul endpoint accessible en `PENDING_PASSWORD`.

## Premier compte et garde-fous

- Au démarrage, **si aucun compte n'existe**, un administrateur est créé à partir
  de `AGORAPILOT_ADMIN_USERNAME` et `AGORAPILOT_ADMIN_PASSWORD`, en
  `PENDING_PASSWORD`, avec tous les droits. Sans ces variables, l'application
  démarre sans compte et le journal l'indique.
- Il reste toujours **au moins un compte actif avec `users:manage`** : on ne
  peut ni se désactiver soi-même ni retirer son propre `users:manage`.
- Un administrateur ne peut pas donner un droit qu'il n'a pas.

## Administration des comptes

`/api/users` (`users:manage`) : liste, création (pseudo + droits, renvoie le mot
de passe provisoire), modification des droits, désactivation et réactivation,
réinitialisation du mot de passe.

## Traçabilité

- Les publications enregistrent leur auteur et leur dernier modificateur. Une
  diffusion enregistre qui l'a programmée, reprise ou annulée. Une suspension
  enregistre qui l'a levée.
- Une table `security_events` journalise les connexions réussies et échouées,
  les verrouillages et toutes les actions d'administration des comptes (qui,
  quoi, sur quel compte, quand).

## Front

- Page de connexion, puis changement obligatoire du mot de passe en
  `PENDING_PASSWORD`.
- Garde de routes, intercepteur : `401` mène à la connexion, `403` à une
  notification « Vous n'avez pas le droit de… ».
- Utilisateur connecté et déconnexion dans le bandeau, page « Mon compte ».
- Actions masquées selon `GET /api/me`.
- Page « Comptes » (`users:manage`) : liste, création avec choix d'un profil puis
  ajustement des droits, désactivation, réinitialisation.

## Ordre de mise en œuvre

1. **Flyway** : migrations versionnées, schéma actuel en migration de base.
2. **Socle d'authentification** (une seule PR back + front, sans quoi la préprod
   serait inutilisable entre les deux) : compte, migrations, connexion et
   session, CSRF, `/api/me`, premier compte, chaîne `denyAll` et droits sur tous
   les endpoints existants, page de connexion, garde, intercepteur.
3. **Administration des comptes** : endpoints `/api/users`, page « Comptes »,
   journal `security_events`.
4. **Règles métier** sur `VERIFIED` et actions masquées selon les droits.
5. **Traçabilité** : auteurs des publications, diffusions et levées de suspension.

Chaque étape vient avec ses tests back et front, et la documentation de mise en
service en préprod (variables d'environnement, premier compte).
