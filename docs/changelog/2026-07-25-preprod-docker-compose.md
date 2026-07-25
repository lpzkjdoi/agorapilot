# Socle de préproduction : Dockerfiles, compose dédié et variables d'environnement

**Date** : 2026-07-25
**Branche** : `claude/preprod-docker-compose`

## Contexte

Le dépôt ne disposait que d'un seul compose, [`docker-compose.yaml`](../../docker-compose.yaml),
purement destiné au développement : sources bind-montées, `mvn spring-boot:run` dans une
image Maven, aucun service front, identifiants `postgres/postgres` en dur. Aucun Dockerfile,
aucun fichier d'exemple pour les variables d'environnement, aucun reverse proxy.

[`gitflow.md`](../gitflow.md) fixait déjà la cible : images publiées sur GHCR, déploiement par
SSH sur le VPS, préproduction automatique à chaque merge sur `develop`. Cette tâche pose le
socle déployable de cette cible. Le VPS dispose déjà d'un Traefik v2.10 (provider Docker,
réseau externe `web`, resolver ACME `myresolver`) sur lequel le compose se branche.

## Changements

### Un point d'entrée unique, sans CORS

Le conteneur front (nginx) sert la SPA **et** proxifie `/api` vers le back. Front et API
partagent donc la même origine :

- `apps/front/src/environments/environment.ts` : `apiUrl` passe de
  `http://localhost:8080/api` à `/api`.
- `apps/front/proxy.conf.json` (nouveau) + `proxyConfig` sur la cible `serve` de
  `angular.json` : `ng serve` proxifie de la même manière, le développement local reste
  fonctionnel et lui aussi en même-origine.

Conséquence : la même image tourne d'un environnement à l'autre sans reconstruction, et
aucune configuration CORS n'entre en jeu.

### Images de production

- `apps/back/Dockerfile` : multi-stage Maven → `eclipse-temurin:21-jre-alpine`, utilisateur
  non-root, `MaxRAMPercentage` pour que la JVM respecte la limite du conteneur. `tzdata` et
  `TZ=Europe/Paris` sont installés parce que `FacebookTokenScheduler` tourne en cron
  `0 0 3 * * *` sans zone explicite : l'heure du conteneur détermine l'heure réelle du
  renouvellement de token.
- `apps/front/Dockerfile` : Node 22 → `nginx:1.27-alpine`. Le builder `application`
  d'Angular écrit dans `dist/front/browser` (vérifié par un build local).
- `apps/front/docker/nginx.conf` : proxy `/api`, fallback SPA, cache immuable sur les
  fichiers hashés, `no-store` sur `index.html`.
- `.dockerignore` de part et d'autre.

### Profil `preprod` du backend

`SecurityConfig` ne déclarait sa chaîne de filtres que sous `@Profile("dev")`. Tout autre
profil retombait sur la configuration par défaut de Spring Security — HTTP Basic sur
l'ensemble des endpoints avec mot de passe généré au démarrage — et le
`corsConfigurationSource()` n'était plus câblé, rendant l'API inutilisable. Le bean couvre
désormais `{"dev", "preprod"}` et a été renommé `permissiveSecurityFilterChain`, son nom
d'origine étant devenu trompeur.

`application-preprod.yaml` (nouveau) coupe les logs SQL et le rechargement à chaud, et
limite l'exposition actuator à `health`. `spring-boot-starter-actuator` est ajouté au
`pom.xml` : `/actuator/health` sert de healthcheck au conteneur back, dont dépend le
démarrage du front (nginx résout le nom `back` au chargement de sa configuration et
refuserait de démarrer sans lui).

### Endpoints d'administration non exposés

`FacebookAdminController` expose `/admin/facebook/bootstrap` et `/admin/facebook/token/status`
sans authentification. Ces routes ne sont **pas** proxifiées par nginx : depuis Internet,
`/admin/...` renvoie l'`index.html` de la SPA. Le back publie son port sur
`127.0.0.1:8080`, ce qui les rend joignables depuis le VPS ou via un tunnel SSH.

### Compose et variables d'environnement

`docker-compose.preprod.yaml` (nouveau) : `db` et `back` sur un réseau interne, `front`
également attaché au réseau externe `web` avec les labels Traefik (router HTTPS +
redirection HTTP, `traefik.docker.network=web` puisque le conteneur est sur deux réseaux).
La base ne publie aucun port. Les blocs `image:` et `build:` coexistent : déploiement
possible dès aujourd'hui depuis un clone, et en `pull` dès que la CI publiera sur GHCR.

Stratégie retenue pour les variables : un unique `.env` posé à côté du compose sur le VPS,
avec trois partis pris.

1. **Pas de `env_file:` global** : chaque service déclare explicitement ses variables, donc
   le mot de passe Postgres n'est pas injecté dans le conteneur back, et le compose
   documente lui-même ce que chaque service consomme.
2. **Variables requises en `${VAR:?message}`** : un déploiement avec une variable manquante
   échoue immédiatement en la nommant, au lieu de démarrer une base avec un mot de passe
   vide.
3. **`.env.preprod.example` versionné** comme contrat. `.gitignore` couvre désormais `.env.*`
   avec une exception pour `.env.*.example`. À noter : le `README.md` réclame aussi un
   `.env.example` pour l'onboarding **en développement** — besoin distinct, non traité ici,
   la TODO reste ouverte.

Ce format est celui que la CI régénérera depuis un GitHub Environment `preprod` (Variables
pour le non-sensible, Secrets pour le reste) : rien à réécrire à la bascule.

### Documentation

`docs/deploiement-preprod.md` (nouveau) : runbook complet — DNS, réseau Traefik, création du
`.env`, commandes de déploiement et de mise à jour, vérifications attendues, tunnel SSH pour
le bootstrap du token, dette connue. `docs/gitflow.md` y renvoie et sa section
« Déploiement » est mise à jour.

## Vérification effectuée

- `cd apps/back && mvn -B verify` → **BUILD SUCCESS**, 14 tests. Dont trois nouveaux dans
  `SecurityConfigPreprodTest` : une seule chaîne de filtres est déclarée sous le profil
  `preprod`, `GET /api/publications` répond **200** (et non 401), `/actuator/health` répond
  200.
- `cd apps/front && npm run build -- --configuration production` → succès, sortie confirmée
  dans `dist/front/browser` (chemin figé dans le `COPY` du Dockerfile).
- `docker-compose.preprod.yaml` : YAML validé par analyse, et inventaire croisé des
  variables interpolées contre `.env.preprod.example` → les 13 variables se correspondent
  exactement, sans manquante ni superflue.

### Non vérifié localement

Ni Docker ni Chrome ne sont installés sur le poste de développement utilisé :

- `docker compose config`, la construction des images et le démarrage bout en bout n'ont pas
  pu être exécutés. Premier lancement à faire sur le VPS en suivant le runbook.
- Les tests unitaires front (Karma) n'ont pas pu tourner (`CHROME_BIN` absent). Ils sont
  exécutés par le workflow **Front CI** à chaque push. Aucun spec ne référence
  `environment.apiUrl` et le build de production passe.
- `nginx -t` n'a pas pu valider `docker/nginx.conf` ; la syntaxe sera vérifiée au premier
  build de l'image.

## Suite

Publication des images sur GHCR, puis workflow de déploiement preprod automatique sur
`develop`. Flyway et l'authentification applicative restent des prérequis avant la
production.
