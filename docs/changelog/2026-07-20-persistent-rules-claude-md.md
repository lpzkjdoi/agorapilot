# 2026-07-20 — Règles de fonctionnement persistantes (CLAUDE.md)

## Contexte

Les règles de fonctionnement du projet (branches `claude/`, PR draft automatique,
tests et documentation systématiques) devaient être rappelées manuellement à chaque
session de travail avec Claude Code. Aucun mécanisme de mémoire persistante n'était
en place (`CLAUDE.md` absent).

## Changements

- Ajout de `CLAUDE.md` à la racine : chargé automatiquement à chaque session, il
  formalise les conventions Git/PR, la règle de tests systématiques (commandes back
  `mvn -B test` et front `npm test`), la règle de documentation systématique
  (changelog) et un rappel de la stack et du domaine métier.
- Ajout de la présente entrée de changelog et mise à jour de `docs/README.md`.

## Vérification

- Aucun code applicatif modifié : ajout de documentation uniquement.
- Cohérence des commandes de test vérifiée avec `.github/workflows/ci.yml` et
  `apps/front/package.json`.
