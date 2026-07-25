# CI : porte d'entrée des PR et déploiement automatique de la préproduction

## Contexte

Deux manques côté CI/CD :

1. **Rien n'empêchait de fusionner une PR rouge.** Back CI et Front CI
   tournaient bien sur les pull requests, mais aucun statut n'était déclaré
   obligatoire côté GitHub. Et les déclarer tels quels n'aurait pas marché :
   filtrés par chemin, ils ne s'exécutent pas du tout quand la PR ne touche pas
   leur périmètre — un statut obligatoire qui ne démarre jamais bloque la PR
   pour toujours.
2. **La préproduction se déployait à la main**, par un `git pull` + build sur le
   VPS. Sur 1 vCPU et 3,9 Go de RAM, un build Maven + Angular passait, mais
   lentement et sous surveillance de l'OOM killer.

## Changements

### `pr-checks.yml` (nouveau) — porte d'entrée des PR

Seul workflow déclenché sur `pull_request` (vers `develop` et `main`) :

- Le job `changes` compare la PR **au point de divergence** avec sa branche
  cible (`git merge-base`), pas à sa tête : seuls les fichiers réellement
  touchés comptent, indépendamment des commits arrivés sur `develop`
  entre-temps. Détection en `git diff` + `grep`, sans action tierce.
- Les jobs `back` et `front` appellent les CI existantes, devenues réutilisables
  (`workflow_call`), et uniquement si le périmètre correspondant a bougé.
- Le job **`all-green`** s'exécute *toujours* (`if: always()`) et échoue dès
  qu'une vérification appelée a échoué ou a été annulée. Un job sauté (rien à
  vérifier) est un succès.

`All checks green` est donc l'unique statut à exiger dans la protection de
branche, et il reste valable si d'autres vérifications sont ajoutées plus tard.

Corollaires sur les workflows existants :

- `ci.yml` et `front-ci.yml` perdent leur déclencheur `pull_request` (remplacé
  par `workflow_call`) et gardent leur déclencheur `push`, toujours filtré par
  chemin.
- `ci.yml` perd `permissions: checks: write`, que rien n'utilisait : un workflow
  appelé ne peut pas demander plus de droits que son appelant, et l'appelant n'a
  besoin que de `contents: read`.
- `concurrency` avec `cancel-in-progress` sur les PR : une nouvelle poussée
  annule la vérification du commit précédent.

### `deploy-preprod.yml` (nouveau) — publication GHCR + déploiement

Déclenché sur `develop` (chemins `apps/**`, compose de préproduction) et
lançable à la main.

1. `build` — matrice back/front, `docker/build-push-action` vers
   `ghcr.io/<owner>/agorapilot-{back,front}`, taguées au SHA du commit et en
   `latest`, avec le cache de build d'Actions (`type=gha`, un scope par
   service). `fail-fast: false` : les deux builds vont au bout.
2. `deploy` — Environment `preprod`, `concurrency` sans annulation (un `up -d`
   interrompu laisserait le stack à moitié recréé) :
   - clé SSH depuis le secret, **empreinte du serveur épinglée** via
     `PREPROD_SSH_KNOWN_HOSTS` plutôt que `StrictHostKeyChecking=no` ;
   - `.env` régénéré depuis les variables et secrets de l'environnement, avec
     `IMAGE_TAG` = SHA du commit ; l'ancien est sauvegardé en `.env.bak` ;
   - `docker compose pull` puis `up -d --no-build --remove-orphans` — `--no-build`
     interdit tout build de secours sur le VPS ;
   - vérification `/actuator/health` sur la boucle locale, puis
     `https://$PREPROD_HOST` **depuis le VPS** en sortant par `--interface
     10.8.0.1`. Le runner GitHub est hors du réseau WireGuard : une requête
     venue de lui recevrait 403. Les 404 transitoires qui suivent un `up -d`
     sont absorbés par les tentatives successives ;
   - le VPS s'authentifie à GHCR avec le `GITHUB_TOKEN` du run, transmis par
     l'entrée standard puis révoqué par un `docker logout` : aucun identifiant
     durable n'est stocké sur la machine, et les images peuvent rester privées.

### Lint front bloquant

`front-ci.yml` perd son `continue-on-error` sur l'étape de lint. Les six erreurs
que le lint remontait jusqu'ici ont été corrigées :

| Fichier | Règle | Correction |
|---|---|---|
| `publication.model.ts`, `occurrence.model.ts` | `@typescript-eslint/consistent-type-definitions` | Trois alias d'objet passés en `interface`. Les alias qui n'en sont pas (`Omit<…>`, `Map<…>`) restent des `type` |
| `dashboard-kpi.component.html` | `@angular-eslint/template/label-has-associated-control` | Les deux `<label>` deviennent des `<span>` : ils n'étiquetaient aucun champ. La mise en forme que la règle globale sur `label` leur donnait est reprise dans `.kpi-title` (et `letter-spacing` ajouté à `.kpi-value`), pour un rendu identique |
| `navbar-button.component.ts` | `@angular-eslint/component-selector` | Sélecteur renommé `button[navBarButton]` → `button[appNavbarButton]`, plus un `eslint-disable-next-line` commenté : la règle exige un sélecteur d'élément, or ce composant habille un `<button>` natif — en faire un élément imposerait un bouton imbriqué, au prix de la sémantique et du focus |

### Dependabot

`.github/dependabot.yml` : npm (`apps/front`), Maven (`apps/back`) et
`github-actions`, chaque lundi. Correctifs et versions mineures regroupés par
écosystème pour limiter le bruit ; les majeures arrivent isolées. Le cas le plus
utile est `github-actions` : les actions sont épinglées sur des tags mutables
(`@v4`, `@v6`), donc ni les correctifs de sécurité ni les dépréciations de
runner ne remontent autrement.

### Scan Trivy des images

Un job `scan` dans `deploy-preprod.yml`, en matrice back/front, sur les images
qui viennent d'être publiées : `HIGH,CRITICAL`, `ignore-unfixed`, rapport publié
dans le résumé du run.

Il est **non bloquant et hors du chemin du déploiement** (`deploy` ne dépend que
de `build`) : une CVE d'image de base n'est pas corrigeable dans l'immédiat, et
bloquer la préproduction là-dessus la rendrait inutilisable. À reconsidérer
avant la mise en production.

## Vérification

Ces workflows ne s'exécutent réellement qu'une fois sur GitHub ; ce qui a pu
être vérifié localement l'a été :

- Syntaxe YAML des quatre workflows et de `dependabot.yml` validée (`js-yaml`)
  — 5/5 OK.
- Les 16 scripts `run:` extraits et passés à `bash -n` — 0 erreur.
- Logique de détection des changements rejouée sur huit diffs représentatifs
  (front seul, back seul, docs seules, mixte, chaque workflow, diff vide) :
  résultats conformes dans les huit cas.
- `cd apps/front && npm run lint` → « All files pass linting ».
- `cd apps/front && npm test` → 13 fichiers, 52 tests verts.
- Rendu des cartes KPI comparé avant/après le passage de `<label>` à `<span>`
  sur `http://localhost:4200/dashboard` : identique.

Restent à valider en conditions réelles, à la première PR et à la première
fusion : l'appel des workflows réutilisables, et le déploiement de bout en bout.

## À faire côté GitHub (non versionnable)

1. **Ruleset** sur `develop` et `main` exigeant le statut `All checks green`
   (détail dans [`gitflow.md`](../gitflow.md#protection-de-branche)).
2. **Environment `preprod`** : variables et secrets listés dans
   [`deploiement-preprod.md`](../deploiement-preprod.md#configuration-attendue).
   ⚠️ `POSTGRES_PASSWORD` doit reprendre le mot de passe **déjà en place** sur le
   VPS, que PostgreSQL conserve depuis l'initialisation du volume.

## Correctif après fusion

Les jobs `scan` ont échoué au premier déploiement, dès la résolution de
l'action : `aquasecurity/trivy-action@0.28.0` n'existe pas. Les tags de cette
action sont **préfixés `v`**, contrairement à la plupart des autres — la version
courante est `v0.36.0`. Corrigé, après vérification que les paramètres utilisés
(`image-ref`, `format`, `output`, `severity`, `ignore-unfixed`, `exit-code`)
existent bien dans cette version, et que les quatre autres actions de ce
workflow — qui n'avaient encore jamais tourné — résolvent correctement.

## Commit

_voir PR_
