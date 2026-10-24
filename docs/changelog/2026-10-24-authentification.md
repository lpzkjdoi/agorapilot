# Comptes et authentification — socle

## Contexte

Toute l'API était ouverte (`permitAll`), et l'accès ne reposait que sur le
réseau. Ce changement est l'étape 2 de la
[proposition sur les comptes et les droits](../proposals/2026-10-24-comptes-et-droits.md) :
le socle back et front, livré d'un bloc pour que la préproduction reste
utilisable.

## Changements

### Back

- **Migration `V2__comptes.sql`** : tables `user_accounts` (pseudo unique en
  minuscules, empreinte BCrypt, statut, échecs, verrouillage, date du dernier
  changement de mot de passe) et `user_permissions` (un droit par ligne).
- **Paquet `account`** :
  - `Permission` : les 13 droits `ressource:action` ;
  - `AccountStatus` : `PENDING_PASSWORD`, `ACTIVE`, `DISABLED` ;
  - `UserAccount` ;
  - `AuthService` : vérification du mot de passe, verrouillage, changement de
    mot de passe, validité d'une session ;
  - `PasswordPolicy` (12 à 128 caractères, différent du pseudo) ;
  - `InitialAdminInitializer` : premier compte depuis `AGORAPILOT_ADMIN_*`.
- **Endpoints** :
  - `POST /api/auth/login` ;
  - `POST /api/auth/logout` (Spring Security) ;
  - `GET /api/me` ;
  - `PUT /api/me/password`.
- **`SecurityConfig` réécrite** : une seule chaîne pour tous les profils.
  - Tout est refusé (`denyAll`) sauf ce que la table des droits permet,
    endpoint par endpoint et méthode par méthode.
  - Sont publics : la connexion et `/actuator/health`.
  - Swagger n'est servi qu'en `dev`, et n'est plus généré en `preprod`.
- **Session côté serveur** :
  - cookie `HttpOnly`, `SameSite=Strict`, et `Secure` en préproduction ;
  - 30 minutes d'inactivité au plus, 12 heures dans tous les cas ;
  - une session neuve à chaque connexion, contre la fixation de session.
- **CSRF** (`csrf.spa()`) : jeton dans le cookie `XSRF-TOKEN`, renvoyé dans
  l'en-tête `X-XSRF-TOKEN`.
- **`AccountRefreshFilter`** relit le compte à chaque requête :
  - un compte désactivé, un mot de passe changé ailleurs ou une session trop
    ancienne coupent la session ;
  - un droit retiré prend effet dès la requête suivante.
- **Connexion** :
  - un seul message d'échec, quelle que soit la cause, en temps constant même
    pour un pseudo inconnu ;
  - verrouillage de 15 minutes après 5 échecs ;
  - un compte au mot de passe provisoire se connecte, mais n'exerce aucun droit
    tant qu'il ne l'a pas changé.

### Front

- **`AuthService`** :
  - compte courant lu une fois ;
  - connexion, déconnexion, changement de mot de passe ;
  - `can(permission)`, utile à l'étape 4.
- **Gardes** :
  - l'application exige un compte actif ;
  - `/mot-de-passe` exige un compte connecté ;
  - `/connexion` renvoie au tableau de bord un compte déjà connecté.
- **Intercepteur** :
  - un `401` renvoie à la connexion, en retenant la page courante ;
  - un `403` sur une action est notifié.
- **XSRF** configuré avec les noms de Spring Security.
- **Pages** « Connexion » et « Choisissez votre mot de passe » / « Changer de
  mot de passe ». Elles sont chargées à la demande : le bundle initial était au
  ras de son budget.
- **Bandeau** : pseudo (lien vers le changement de mot de passe) et
  « Se déconnecter ». Le bandeau et les alertes de suspension n'apparaissent
  qu'une fois connecté.
- **Routes** : `/` et toute adresse inconnue mènent au tableau de bord.

### Mise en service

- `docker-compose.preprod.yaml`, `deploy-preprod.yml` et `.env.preprod.example` :
  `AGORAPILOT_ADMIN_USERNAME` (Variable) et `AGORAPILOT_ADMIN_PASSWORD` (Secret),
  facultatifs.
- `deploiement-preprod.md` : section « Comptes et connexion » (premier compte,
  sessions, recette `curl` pour les endpoints `/admin/**` depuis le VPS) ; les
  vérifications après déploiement attendent désormais un `401` sur l'API.
- Développement local : ajouter `AGORAPILOT_ADMIN_USERNAME` et
  `AGORAPILOT_ADMIN_PASSWORD` au `.env` (lu par `docker-compose.yaml`), ou à
  l'environnement du back, pour disposer d'un premier compte.

## Vérification

- `mvn -B test` : 413 tests, dont :
  - `EndpointPermissionsTest` (87) : pour chaque endpoint, 401 en anonyme, 403
    avec tous les droits sauf le bon, ouvert avec le seul bon droit ; refus d'une
    route sans règle ; CSRF exigé ;
  - `AuthFlowTest` (17), contre H2 avec une horloge pilotée : connexion,
    fixation de session, réponse unique, verrouillage puis déverrouillage, mot de
    passe provisoire, règles du nouveau mot de passe, sessions coupées par un
    changement de mot de passe, par une désactivation ou au bout de 12 heures,
    droit retiré pris en compte à la requête suivante ;
  - `InitialAdminInitializerTest` (5) et `SecurityConfigPreprodTest` (5).

  Les tests de contrôleurs passent derrière la vraie chaîne (`@ApiControllerTest`).
- `npm test` : 503 tests (service, gardes, intercepteur, deux pages, bandeau,
  coquille, routes) ; `ng lint` et `ng build` sans avertissement.
- De bout en bout, avec le vrai back sur un PostgreSQL 17 local et le front par
  `ng serve` :
  - redirection vers la connexion avec la page demandée ;
  - refus d'un mauvais mot de passe ;
  - premier compte créé au démarrage puis redirigé vers le changement de mot de
    passe ;
  - tableau de bord avec les vrais droits ;
  - création d'une publication (jeton CSRF transmis) ;
  - déconnexion.

  Avec `curl` : cookie de session `HttpOnly; SameSite=Strict`, API fermée en
  anonyme, recette de la doc vérifiée en profil `preprod`.

## Reste à faire

Étapes suivantes de la proposition :

- administration des comptes (`/api/users`, page « Comptes », journal
  `security_events`) ;
- règles métier sur `VERIFIED` et actions masquées selon les droits ;
- traçabilité.

D'ici là, un compte se crée seulement par le premier démarrage, et un `403` se
découvre au clic.
