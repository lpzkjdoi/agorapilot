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

## Vérification

Ces workflows ne s'exécutent réellement qu'une fois sur GitHub ; ce qui a pu
être vérifié localement l'a été :

- Syntaxe YAML des quatre workflows validée (`js-yaml`) — 4/4 OK.
- Les 15 scripts `run:` extraits et passés à `bash -n` — 0 erreur.
- Logique de détection des changements rejouée sur huit diffs représentatifs
  (front seul, back seul, docs seules, mixte, chaque workflow, diff vide) :
  résultats conformes dans les huit cas.

Restent à valider en conditions réelles, à la première PR et à la première
fusion : l'appel des workflows réutilisables, et le déploiement de bout en bout.

## À faire côté GitHub (non versionnable)

1. **Ruleset** sur `develop` et `main` exigeant le statut `All checks green`
   (détail dans [`gitflow.md`](../gitflow.md#protection-de-branche)).
2. **Environment `preprod`** : variables et secrets listés dans
   [`deploiement-preprod.md`](../deploiement-preprod.md#configuration-attendue).
   ⚠️ `POSTGRES_PASSWORD` doit reprendre le mot de passe **déjà en place** sur le
   VPS, que PostgreSQL conserve depuis l'initialisation du volume.

## Commit

_voir PR_
