# Administration des comptes et journal de sécurité

## Contexte

C'est l'étape 3 de la
[proposition sur les comptes et les droits](../proposals/2026-10-24-comptes-et-droits.md).
Après le socle d'authentification, le seul compte possible était le premier
administrateur, créé au démarrage.

## Changements

### Back

- **Migration `V3__journal_securite.sql`** : table `security_events` (heure,
  type, acteur, compte visé, détails), avec un index sur l'heure.
- **`SecurityEventService`** inscrit au journal, dans la transaction de
  l'appelant :
  - connexions réussies ;
  - connexions refusées, avec leur cause : pseudo inconnu, mot de passe erroné,
    compte désactivé ou verrouillé ;
  - verrouillages ;
  - changements de mot de passe ;
  - création du premier compte ;
  - toutes les actions d'administration.
- **`AccountAdminService`** et **`AccountAdminController`**, réservés au droit
  `users:manage` :
  - `GET /api/users` : la liste, avec les droits attribués et le verrouillage en
    cours ;
  - `POST /api/users` : pseudo et droits. La réponse contient un mot de passe
    provisoire (16 caractères aléatoires, alphabet sans confusions), donné une
    seule fois ;
  - `PUT /api/users/{id}/permissions` : remplacement complet ;
  - `POST /api/users/{id}/disable` ;
  - `POST /api/users/{id}/password-reset` : nouveau mot de passe provisoire,
    sessions coupées, verrouillage levé. C'est aussi la seule façon de réactiver
    un compte, qui ne retrouve donc jamais son ancien mot de passe ;
  - `GET /api/security-events?limit=` : le plus récent d'abord, 200 au plus.
- **Garde-fous** :
  - aucune action d'administration sur son propre compte. L'administrateur qui
    agit reste donc actif avec `users:manage`, et il en reste toujours un ;
  - on ne donne ni ne retire que des droits qu'on détient ;
  - le service revérifie `users:manage` lui-même, sans compter sur la seule
    chaîne de sécurité.
- **`SecurityConfig`** : règles pour `/api/users/**` et `/api/security-events`.

### Front

- **Page « Comptes »** (`/comptes`), chargée à la demande et gardée par
  `permissionGuard('users:manage')` :
  - le tableau des comptes : statut, verrouillage, profil ou nombre de droits,
    dernière connexion ;
  - les actions « Droits », « Réinitialiser le mot de passe » ou « Réactiver »,
    et « Désactiver », confirmées en deux temps ;
  - le journal de sécurité, où les refus et les verrouillages sont en rouge.
- **Sélecteur de droits** : quatre profils qui pré-cochent les cases, à ajuster
  un par un. Un droit que l'administrateur ne détient pas reste figé.
- **Modales** :
  - création et droits ;
  - mot de passe provisoire, avec un bouton de copie. Elle ne se ferme que sur
    « J'ai noté le mot de passe ».
- **Entrée « Comptes »** dans le bandeau, pour les seuls comptes `users:manage`.
- **Pages chargées à la demande** : Campagnes, Publications et Médiathèque le
  sont désormais, comme le Calendrier. Le bundle initial passe de 499 kB à
  370 kB : il dépassait son budget de 500 kB à chaque ajout.

## Vérification

- `mvn -B test` : 448 tests, dont :
  - `AccountAdminFlowTest` (17), avec de vraies sessions :
    - création et mot de passe provisoire utilisable ;
    - pseudo pris, ou hors règle, droit inconnu ;
    - droits non détenus, aussi bien à donner qu'à retirer ;
    - rien sur son propre compte ;
    - désactivation qui coupe les sessions ;
    - réinitialisation qui coupe les sessions, réactive et déverrouille ;
    - liste, contenu et ordre du journal ;
  - la table des droits complétée de 6 endpoints. Elle s'appuie désormais sur de
    vrais comptes en base, relus par le filtre comme en production.
- `npm test` : 539 tests (service, catalogue des droits, sélecteur, deux
  modales, page, garde, bandeau, routes) ; `ng lint` et `ng build` sans
  avertissement.
- De bout en bout, avec le vrai back sur PostgreSQL local :
  - connexion qui ramène sur `/comptes` ;
  - création d'un compte « Lecteur » et mot de passe provisoire affiché ;
  - désactivation confirmée, puis « Réactiver » proposé ;
  - journal à jour.

## Reste à faire

Étapes 4 et 5 de la proposition :

- règles sur `VERIFIED` et actions masquées selon les droits ;
- traçabilité des auteurs.
