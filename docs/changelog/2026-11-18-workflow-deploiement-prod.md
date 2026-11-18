# Workflow de déploiement de la production

**Date** : 2026-11-18
**Branche** : `claude/deploy-prod`

## Contexte

Premier chantier de la chaîne de mise en production, choisi par Maxime après
l'état des lieux du VPS et de GitHub du même jour. Décisions déjà prises : la
prod tourne sur le VPS actuel, à côté de la préprod ; domaine `agorapilot.fr`
(`www` redirigé) ; app Meta distincte de celle de la préprod.

L'état des lieux a aussi montré que deux items de la roadmap étaient déjà
couverts : la protection des branches (ruleset actif) et la rotation des logs
(`/etc/docker/daemon.json`, 10 Mo × 3).

## Changements

- **`.github/workflows/deploy-prod.yml`** :
  - `check` : la version doit être `X.Y.Z` (pas de pré-version) et le tag doit
    être contenu dans `main` ;
  - `build` : images `prod-X.Y.Z` uniquement (jamais `latest`), badge front sur
    `prod` et la version, conf nginx de prod, cache Actions séparé de la préprod ;
  - `scan` : Trivy, non bloquant comme en préprod ;
  - `deploy` : environnement GitHub `production` (approbation manuelle), même
    déploiement SSH que la préprod, dans `/opt/agorapilot/prod` ; santé du back
    sur `127.0.0.1:8081`, puis `https://<PROD_HOST>` vérifié depuis le runner ;
  - relance manuelle avec une version déjà taguée : les images existantes sont
    redéployées sans rebuild, ce qui sert de retour arrière.
- **`docker-compose.prod.yaml`** : projet `agorapilot-prod`, conteneurs, réseau
  interne, volumes et routeurs Traefik propres ; pas de filtre IP ; pas de
  `build:` ; `IMAGE_TAG` obligatoire ; limites mémoire (back 768 Mo, base
  256 Mo, front 64 Mo) ; `www` redirigé vers le domaine principal, sur un
  routeur et un certificat à part.
- **`.env.prod.example`** : contrat de variables de la production.
- **Profil Spring `prod`** (`application-prod.yaml`, copie du profil `preprod`),
  pour que les notifications portent le nom de l'environnement réel.
- **Front** : `docker/nginx.prod.conf` (sans le proxy Swagger), choisi au build
  par l'argument `NGINX_CONF` du Dockerfile ; la préprod garde `nginx.conf` par
  défaut.
- **Préprod** : tags d'image `preprod-<sha>` et `preprod-latest` au lieu du SHA
  seul et de `latest`. Le prochain déploiement réécrit le `.env` du VPS : rien à
  faire à la main.
- **Docs** : runbook [`deploiement-prod.md`](../deploiement-prod.md), `gitflow.md`,
  `deploiement-preprod.md`, roadmap du README.

## Vérification effectuée

- `mvn -B verify` : 469 tests, 0 échec, dont `SecurityConfigProdTest`, qui rejoue
  les 5 vérifications de sécurité de la préprod avec le profil `prod` (API fermée
  aux anonymes, healthcheck ouvert, Swagger absent, jeton CSRF).
- `docker compose -f docker-compose.prod.yaml config` sur le VPS (Compose 2.33.1),
  dans un dossier temporaire supprimé ensuite, avec un `.env` factice : valide ;
  limites mémoire, port `127.0.0.1:8081` et redirection `www` bien interprétés.
- Syntaxe YAML des deux workflows et des deux compose vérifiée.
- **Non vérifié** : le workflow de prod n'a jamais tourné (pas d'environnement
  `production`, pas de tag) ; actionlint et Docker ne sont pas installés sur le
  poste, donc ni lint des workflows ni build local de l'image front avec
  `NGINX_CONF`. Le build front par défaut sera vérifié par le déploiement de la
  préprod à la fusion.

## Reste à faire

- Côté Maxime : environnement GitHub `production` (relecteur, règles `v*` et
  `main`, variables et secrets), DNS `agorapilot.fr`, app Meta de production.
- Images de base épinglées par digest, durcissement, sauvegardes, sonde
  externe (roadmap du README).

## Commit

_voir PR_
