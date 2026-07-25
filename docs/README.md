# Documentation AgoraPilot

Ce dossier centralise la documentation technique du projet qui ne trouve pas naturellement sa place dans le code lui-même.

## Structure

- [`gitflow.md`](./gitflow.md) — modèle de branches, cycle de contribution, versionnage et conventions CI/CD. Référence mise à jour quand le processus évolue.
- [`deploiement-preprod.md`](./deploiement-preprod.md) — runbook de la préproduction sur VPS : prérequis, variables d'environnement, commandes de déploiement et vérifications.
- [`changelog/`](./changelog) — un compte-rendu par tâche/évolution significative : contexte, changements, vérification effectuée, lien vers le commit correspondant. Une nouvelle entrée est ajoutée à chaque tâche de développement.

## Changelog

| Date | Sujet | Commit |
|---|---|---|
| 2026-07-12 | [Suppression de Tailwind CSS](./changelog/2026-07-12-remove-tailwind-css.md) | [`8fa2721`](https://github.com/lpzkjdoi/agorapilot/commit/8fa2721f1eb09ced7c881e66c3e04486fc0a67c3) |
| 2026-07-15 | [Durcissement du renouvellement du token Facebook](./changelog/2026-07-15-facebook-token-renewal-hardening.md) | [`30b31d4`](https://github.com/lpzkjdoi/agorapilot/commit/30b31d4c4850f1a1124010c04415317e3ec4d3b0) |
| 2026-07-18 | [CI/CD : Front CI, durcissement Back CI et gitflow](./changelog/2026-07-18-cicd-pipelines-gitflow.md) | _(voir branche `develop`)_ |
| 2026-07-20 | [Migration de la navbar (gauche → haut)](./changelog/2026-07-20-navbar-top-migration.md) | _(voir branche `claude/session-xfe1tv`)_ |
| 2026-07-20 | [Tests unitaires des composants front + convention](./changelog/2026-07-20-front-unit-tests.md) | _(voir branche `claude/session-xfe1tv`)_ |
| 2026-07-20 | [Règles de fonctionnement persistantes (CLAUDE.md)](./changelog/2026-07-20-persistent-rules-claude-md.md) | _voir PR_ |
| 2026-07-20 | [Lien vers la maquette Figma dans CLAUDE.md](./changelog/2026-07-20-lien-maquette-figma.md) | _voir PR_ |
| 2026-07-25 | [Socle de préproduction : Dockerfiles, compose dédié et variables d'environnement](./changelog/2026-07-25-preprod-docker-compose.md) | [`d060f7e`](https://github.com/lpzkjdoi/agorapilot/commit/d060f7e) |
| 2026-07-25 | [Corrections issues de la première mise en service de la préproduction](./changelog/2026-07-25-corrections-post-mise-en-service.md) | _voir PR_ |
