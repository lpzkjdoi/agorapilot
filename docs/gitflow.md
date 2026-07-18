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
3. Merger dans `develop` en `--no-ff` (via Pull Request quand une revue est souhaitée) → déploie la **preprod**.
4. Quand la preprod est validée : merger `develop` → `main`, puis **taguer** `vX.Y.Z` → déploie la **prod** (approbation manuelle).

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

Les workflows sont filtrés par chemin : une modification ne déclenche que la CI concernée.

| Workflow | Fichier | Déclencheurs | Étapes |
|---|---|---|---|
| Back CI | `.github/workflows/ci.yml` | PR / push sur `main`, `develop` (chemins `apps/back/**`) | `mvn verify` (build + tests + jar), upload du jar |
| Front CI | `.github/workflows/front-ci.yml` | PR / push sur `main`, `develop` (chemins `apps/front/**`) | `npm ci`, lint (non bloquant), tests headless, build prod |

> **Lint front non bloquant** pour l'instant (`continue-on-error`) : il remonte les écarts sans casser la CI. À basculer en bloquant une fois les règles arbitrées et le code aligné.

## Déploiement (à venir)

Cible retenue : **images Docker publiées sur GHCR**, puis déploiement par **SSH sur le VPS** (`docker compose pull && up -d`).

- **Preprod** : automatique à chaque merge sur `develop`.
- **Prod** : sur tag `vX.Y.Z` (ou merge `main`), avec un GitHub Environment `production` protégé (approbation manuelle).

Prérequis avant le premier déploiement prod :
- Dockerfiles de production (back multi-stage Maven→JRE, front build→nginx).
- Migrations de base versionnées (Flyway) en remplacement de `ddl-auto: update`.

## Recommandations d'outillage GitHub

- **Protection de branches** sur `main` et `develop` : CI verte requise avant merge (à activer côté GitHub).
- Pull Requests recommandées pour toute contribution destinée à `develop` / `main` afin de conserver la trace et la revue.
