# 2026-11-21 — Images de prod allégées et épinglées par digest

## Contexte

Dernier prérequis de prod de [`gitflow.md`](../gitflow.md) : épingler les images
de base par digest, pour que deux builds du même arbre git donnent la même
image. Maxime a demandé en même temps des images de prod aussi légères que
possible, sans perte de capacité : rien de ce dont l'application ne se sert.

## Changements

| Image | Avant | Après |
|---|---|---|
| Back, exécution | `eclipse-temurin:21-jre-alpine` | `alpine:3.24.4` + JRE assemblé par **jlink** depuis `eclipse-temurin:21.0.13.1_2-jdk-alpine-3.24` |
| Back, build | `maven:3.9.9-eclipse-temurin-21` (Debian) | `maven:3.9.16-eclipse-temurin-21-alpine` |
| Front, exécution | `nginx:1.27-alpine` | `nginx:1.30.7-alpine3.24-slim` (branche stable) |
| Front, build | `node:22-alpine` | `node:22.23.5-alpine3.24` (Node 22, comme la CI front) |
| Base de prod | `postgres:16` (Debian) | `postgres:16.16-alpine3.24`, collation ICU `fr-FR` |

Toutes les images sont épinglées en `tag@sha256:<digest>` (digest de l'index
multi-architecture). Pour monter de version : relever le digest du nouveau tag
et remplacer les deux, dans une PR. Docker CI éprouve alors le résultat.

**Back.** L'image JRE de Temurin embarque des polices, les locales musl,
gnupg, openssl, coreutils et tous les modules Java. Le JRE jlink ne garde que :

- les modules que `jdeps` détecte dans le code et les dépendances, recalculés à
  chaque build : une nouvelle dépendance qui en exige un autre l'embarque d'elle-même ;
- ceux que `jdeps` ne voit pas, car chargés dynamiquement : `jdk.crypto.ec`
  (TLS vers Facebook et Discord), `jdk.localedata` restreint à `fr` et `en`
  (mois en français), `jdk.management` (actuator), `jdk.zipfs`.

C'est la même JVM HotSpot, aux mêmes performances. Sur Alpine nu, seul
`tzdata` est ajouté. Les polices sont inutiles, car aucun texte n'est dessiné
dans les images, et les certificats aussi, car la JVM a son propre magasin.
`LANG=C.UTF-8` garde l'UTF-8 pour les noms de fichiers. AWT tourne en
`headless`.

**Front.** La variante `slim` est nginx avec ses modules intégrés (gzip, proxy,
en-têtes, cache), sans les modules dynamiques njs, geoip, image-filter et xslt,
ni envsubst. Aucune de nos confs ne s'en sert.

**Base.** musl ne trie les chaînes que par octet : « É » passe après « z » et
les majuscules avant les minuscules. La base de prod est donc initialisée en
ICU `fr-FR` (`POSTGRES_INITDB_ARGS`), un tri français qui ne dépend pas de la
libc. Le réglage ne joue qu'à la création du volume, qui n'existe pas encore en
prod. Aucune migration ne dépend d'une collation.

**Préprod.** Elle partage les Dockerfiles, donc les images back et front, dès
la fusion. Sa base reste `postgres:16` Debian : passer son volume existant sous
musl imposerait un `REINDEX DATABASE`.

**Healthcheck du front** (prod et préprod) : il sonde `127.0.0.1` et non plus
`localhost`. Sur un Docker avec IPv6, comme sur les runners GitHub, `localhost`
se résout d'abord en `::1`, où nginx n'écoute pas. Docker CI l'a révélé : le
front restait `unhealthy` sans qu'aucune sonde n'arrive à nginx.

### Docker CI

Nouveau workflow [`docker-ci.yml`](../../.github/workflows/docker-ci.yml),
appelé par `pr-checks.yml` (donc compté dans « All checks green ») quand un
Dockerfile, une conf nginx, un compose de VPS, le `pom.xml` du back ou le
workflow lui-même changent. Il construit les images de prod avec les mêmes
arguments que `deploy-prod.yml` et les lance avec le vrai
`docker-compose.prod.yaml`. Il vérifie ensuite :

- **JRE** ([`RuntimeSmoke.java`](../../.github/docker-smoke/RuntimeSmoke.java),
  lancé avec le `java` de l'image) : aplatissement et ré-encodage JPEG en
  headless, mois en français, fuseau `Europe/Paris`, encodage UTF-8, TLS 1.3
  vers graph.facebook.com ;
- **back** : santé, jeton CSRF puis connexion, dépôt d'une image et relecture
  à l'identique ;
- **front** : SPA servie, `/api` proxifié (401 pour un anonyme), aucune
  description OpenAPI exposée ;
- **base** : collation `i|fr-FR`, et `abricot, eau, Éléphant, zèbre` dans cet
  ordre.

## Vérification

Tailles compressées (téléchargées par le VPS), mesurées par Docker CI :

| Image | Avant | Après |
|---|---|---|
| Back | ≈ 146 Mo (JRE Temurin 77 Mo + jar) | 106 Mo, dont 69 Mo pour le jar et ≈ 33 Mo pour le JRE |
| Front | ≈ 23 Mo | 5 Mo |
| Base | 161 Mo | 116 Mo |

Le jar Spring Boot pèse désormais les deux tiers de l'image back. Le réduire
voudrait dire retirer des dépendances, pas changer d'image.

- `RuntimeSmoke` passe sur un JDK complet en local (référence) et dans l'image.
  Le JRE embarque 24 modules (liste affichée par Docker CI).
- Premier passage de Docker CI : tout vert jusqu'au front `unhealthy` (cf.
  healthcheck ci-dessus) ; second passage après correction : voir la PR.
- Back CI et Front CI verts. Aucun code applicatif modifié.

Commit : voir la PR de la branche `claude/images-docker-legeres`.
