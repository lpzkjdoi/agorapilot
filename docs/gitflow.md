# Gitflow & CI/CD

Modèle de branches et conventions de déploiement du projet. Cette page est la référence ; elle est mise à jour quand le processus évolue.

## Branches

| Branche | Rôle | Déploiement |
|---|---|---|
| `main` | Reflète la **production**. Jamais de commit direct. Toujours déployable. | Tag `vX.Y.Z` (ou merge sur `main`) → **prod** (approbation manuelle) |
| `develop` | Branche d'**intégration**, toujours cohérente. Base de toutes les branches de travail. | Chaque merge → **preprod** (automatique) |
| `feature/*` | Une nouvelle fonctionnalité. Part de `develop`. | CI uniquement (tests / lint / build) |
| `fix/*` | Une correction non urgente. Part de `develop`. | CI uniquement |
| `hotfix/*` | Correction urgente en production. Part de `main`. | Après merge : voir cycle hotfix |
| `claude/*` | Travail produit par l'assistant. Se comporte comme une branche de travail (part de `develop`, sauf hotfix). | CI uniquement |

> Pas de branche `release/*` : phase de stabilisation jugée superflue pour la taille actuelle du projet.

## Schéma

```
main ──────●───────────────────●──────────►   (prod ; toujours déployable)
            \                  /│
             \         (merge / │ tag vX.Y.Z ──► déploiement PROD (approbation manuelle)
              \              / \│
develop ───●───●───●───●───●────●──────────►   (intégration ; déploiement PREPROD auto)
            \  /   \  /
   feature/* ●     ●     claude/* ...           (une branche par unité de travail)
```

## Cycle d'une modification

1. Brancher depuis `develop` : `feature/xxx`, `fix/xxx` (ou `claude/xxx`).
2. Pousser la branche → la CI tourne (back et/ou front selon les chemins modifiés).
3. **Ouvrir une Pull Request en draft vers `develop`** (voir [Pull Requests en draft](#pull-requests-en-draft)).
4. Quand le travail est prêt et la CI verte : passer la PR en « ready for review », puis merger dans `develop` en `--no-ff` → déploie la **preprod**.
5. Quand la preprod est validée : merger `develop` → `main`, puis **taguer** `vX.Y.Z` → déploie la **prod** (approbation manuelle).

### Pull Requests en draft

Toute PR est **créée en draft**, sans exception. Le draft signale que le travail est en
cours : la CI tourne et le diff est relisable, mais la PR ne réclame pas encore de revue et
ne peut pas être mergée par inadvertance. Le passage en « ready for review » est une action
délibérée, faite quand la branche est réellement finie et la CI verte.

```bash
gh pr create --draft --base develop --title "..." --body "..."
```

Convertir une PR existante dans un sens ou dans l'autre :

```bash
gh pr ready --undo <numéro>   # repasse en draft
gh pr ready <numéro>          # marque prête pour la revue
```

### Cycle hotfix

1. Brancher depuis `main` : `hotfix/xxx`.
2. Merger dans `main` **et** dans `develop` (pour ne pas perdre le correctif).
3. Taguer `vX.Y.(Z+1)` → déploie la prod.

## Versionnage

**SemVer** via tags `vX.Y.Z`. Un tag sur `main` promeut la version correspondante en production.

- `MAJOR` : changement incompatible.
- `MINOR` : fonctionnalité rétrocompatible.
- `PATCH` : correction rétrocompatible.

## Intégration continue

| Workflow | Fichier | Déclencheurs | Étapes |
|---|---|---|---|
| PR checks | `.github/workflows/pr-checks.yml` | PR vers `develop`, `main` | Détecte ce que la PR touche, appelle les CI concernées, publie le statut **`All checks green`** |
| Back CI | `.github/workflows/ci.yml` | push sur `main`, `develop` (chemins `apps/back/**`) + appel par PR checks | `mvn verify` (build + tests + jar), upload du jar |
| Front CI | `.github/workflows/front-ci.yml` | push sur `main`, `develop` (chemins `apps/front/**`) + appel par PR checks | `npm ci`, lint, tests Vitest, build prod |
| Deploy preprod | `.github/workflows/deploy-preprod.yml` | push sur `develop` (chemins `apps/**`, compose preprod) + manuel | Publie les images sur GHCR, scan Trivy (non bloquant), régénère le `.env` du VPS, `pull` + `up -d`, vérifie la santé |

Les montées de dépendances sont proposées en PR par Dependabot
([`.github/dependabot.yml`](../.github/dependabot.yml)), chaque lundi, pour npm,
Maven et les actions GitHub. Ces PR passent par « PR checks » comme les autres :
une montée qui casse les tests ne peut pas être fusionnée.

### Pourquoi un workflow « PR checks »

Back CI et Front CI sont filtrés par chemin, pour ne pas payer une CI Java sur
une modification de CSS. Mais un statut filtré ne s'exécute pas du tout quand le
chemin ne correspond pas : déclaré obligatoire dans la protection de branche, il
resterait indéfiniment « en attente » et bloquerait la PR.

`pr-checks.yml` lève cette contradiction. Il est seul déclenché sur
`pull_request`, compare la PR au point de divergence avec sa branche cible,
n'appelle que les CI utiles, puis publie **un statut unique qui, lui, s'exécute
toujours** : `All checks green`. Il échoue dès qu'une vérification appelée a
échoué ou a été annulée ; un job simplement sauté (rien à vérifier de ce côté)
est un succès.

### Protection de branche

`develop` et `main` sont protégées : pas de poussée directe, et fusion
conditionnée à un seul statut obligatoire, **`All checks green`**. C'est
volontairement le seul : ajouter demain une CI (mobile, e2e, scan de sécurité)
suffit à la rendre bloquante sans retoucher la configuration du dépôt.

Réglage côté GitHub — *Settings → Rules → Rulesets → New branch ruleset* :

| Réglage | Valeur |
|---|---|
| Target branches | `develop`, `main` |
| Restrict deletions | ✔ |
| Require a pull request before merging | ✔ |
| Require status checks to pass | ✔ → ajouter `All checks green` |
| Require branches to be up to date before merging | ✔ |
| Block force pushes | ✔ |

> **Le lint front est bloquant** depuis le 2026-07-25, le code ayant été aligné sur les règles. La seule exception est documentée sur place, dans `navbar-button.component.ts` : ce composant s'applique en attribut sur un `<button>` natif, là où `@angular-eslint/component-selector` attend un sélecteur d'élément.

## Déploiement

Cible retenue : **images Docker publiées sur GHCR**, puis déploiement par **SSH sur le VPS** (`docker compose pull && up -d`).

- **Preprod** : automatique à chaque merge sur `develop`.
- **Prod** : sur tag `vX.Y.Z` (ou merge `main`), avec un GitHub Environment `production` protégé (approbation manuelle).

Le socle de la préproduction est en place : Dockerfiles de production, compose dédié
branché sur le Traefik du VPS, et contrat de variables d'environnement. Le déploiement
manuel reste décrit dans **[`deploiement-preprod.md`](./deploiement-preprod.md)**, qui
documente aussi le déploiement automatique.

**Preprod : automatisée.** `deploy-preprod.yml` publie les deux images sur GHCR
(taguées au SHA du commit), régénère le `.env` du VPS depuis l'environnement GitHub
`preprod`, puis `docker compose pull && up -d --no-build`. Plus aucun build sur le
VPS, dont le vCPU unique s'en accommodait mal.

Reste à faire :
- Workflow de déploiement prod (tag `vX.Y.Z`, Environment `production` avec approbation manuelle).

Prérequis avant le premier déploiement prod :
- Migrations de base versionnées (Flyway) en remplacement de `ddl-auto: update`.
- Authentification applicative : la chaîne de filtres actuelle est permissive.

## Recommandations d'outillage GitHub

- **Protection de branches** sur `main` et `develop` : CI verte requise avant merge (à activer côté GitHub).
- Pull Requests **obligatoires en draft** pour toute contribution destinée à `develop` / `main`,
  afin de conserver la trace et la revue.

### GitHub CLI (`gh`)

`gh` est l'outil de référence du projet pour tout ce qui relève de la plateforme (PR, CI,
issues), là où `git` ne couvre que le dépôt. Installation et connexion, une seule fois par
poste :

```bash
brew install gh
gh auth login --hostname github.com --git-protocol ssh --web
```

`gh auth login` est interactif (code à coller sur github.com) : il doit être lancé
manuellement. Vérifier ensuite avec `gh auth status` que le compte actif est bien celui qui
a les droits d'écriture sur le dépôt. Commandes courantes une fois connecté :

| Besoin | Commande |
|---|---|
| Créer la PR draft | `gh pr create --draft --base develop` |
| Voir l'état d'une PR | `gh pr view <numéro>` |
| Suivre la CI en cours | `gh run watch` |
| Lire les logs d'un job en échec | `gh run view --log-failed` |
