# Une image par environnement : prérequis prod et commentaires remis d'équerre

- **Date** : 2026-08-22
- **Branche** : `claude/prerequis-image-par-environnement` (depuis `develop`)
- **Périmètre** : documentation et commentaires — aucun changement de comportement

## Contexte

L'[indicateur d'environnement](./2026-08-22-indicateur-environnement.md) fige
l'environnement servi dans le bundle front (`build-info.ts`, réécrit par la CI
avant le build de l'image). L'image front n'est donc plus portable d'un
environnement à l'autre.

Or trois endroits du dépôt affirmaient l'inverse. Chacun soudait deux
affirmations distinctes :

1. le raisonnement **origine unique / CORS** — `apiUrl: '/api'` en relatif, nginx
   proxifie, donc aucune URL d'API à substituer par environnement. Toujours vrai ;
2. la conclusion **« donc l'image est réutilisable telle quelle »**. Devenue fausse.

Cette entrée dissocie les deux, et acte la direction prise : une image par
environnement, assumée.

## Pourquoi une image par environnement — et pourquoi pas pour le poids

Le déclencheur de la discussion était la légèreté de l'image de production. Les
chiffres ne la soutiennent pas : le build de production du front fait **424 Ko en
5 fichiers** (348 Ko de `main.js`, 36 Ko de polyfills, 32 Ko de favicon, 4 Ko de
CSS, 4 Ko d'`index.html`), le reste de l'image étant `nginx:1.27-alpine` — le
stage `node:22-alpine` et les `node_modules` sont déjà jetés par le multi-stage.
Il n'y a rien d'environnement-spécifique dans ces 424 Ko à retirer pour la prod :
une image prod dédiée pèserait, à quelques kilo-octets près, le poids de l'image
preprod. Les leviers de poids réels sont ailleurs et ne dépendent d'aucun
environnement : base `alpine-slim`, et surtout l'image du back (JRE + fat jar).

Ce qui justifie la séparation, c'est la **surface exposée**, dans l'autre sens :
la preprod a le droit d'être plus grasse. Le proxy Swagger d'`nginx.conf` est
conservé en preprod parce que c'est un environnement de test ; il n'a rien à
faire en production.

## Changements

### `docs/gitflow.md` — trois entrées aux prérequis prod

Ajoutées à la liste « Prérequis avant le premier déploiement prod » :

- **une image par environnement, assumée comme telle**, avec un `nginx.prod.conf`
  sans le proxy Swagger ;
- **des tags d'image porteurs de l'environnement** (`agorapilot-front:preprod-<sha>`,
  `agorapilot-front:prod-<version>`) plutôt que l'`IMAGE_TAG` commun actuel — une
  image dont l'environnement est figé dedans mais pas dans son nom se déploie un
  jour au mauvais endroit, et le badge affiche « preprod » en production ;
- **des images de base épinglées par digest** — c'est le prix de ce choix :
  l'artefact validé en preprod n'est plus celui qui tourne en prod, donc tout ce
  qui bouge entre deux builds du même arbre git devient une source de « ça
  marchait en preprod ». Le lockfile couvre npm, le digest couvre les tags
  glissants (`nginx:1.27-alpine`, `node`, `maven`).

### Commentaires remis d'équerre

| Fichier | Avant | Après |
|---|---|---|
| `apps/front/src/environments/environment.ts` | « Aucune reconstruction de l'image n'est donc nécessaire d'un environnement à l'autre » | la portée est ramenée à `apiUrl`, et le renvoi vers `build-info.ts` explicite ce qui, lui, est figé au build |
| `apps/front/docker/nginx.conf` | « permet à l'image d'être réutilisée telle quelle d'un environnement à l'autre » | idem, et le fichier assume d'être la conf de **préproduction** (le proxy Swagger n'ira pas en prod) |
| `docs/deploiement-preprod.md` | « la même image tourne en preprod comme en prod sans reconstruction » | la prod aura son propre build du front, ce que fera de toute façon un workflow déclenché sur tag |

## Vérification

- `cd apps/front && npm test` → 24 fichiers, 168 tests verts (inchangé : seuls des
  commentaires ont bougé côté code).
- `npm run lint` propre, build de production OK.

## Commit

_voir PR_
