# CI/CD : Front CI, durcissement Back CI et formalisation du gitflow

- **Date** : 2026-07-18
- **Branche** : `claude/github-cicd-pipelines-wghw2i` → mergée dans `develop`
- **Périmètre** : `.github/workflows`, `apps/front` (outillage), `docs`

## Contexte

La CI ne couvrait que les tests backend (`mvn test`). Le front Angular n'avait aucune CI (ni build, ni test, ni lint), et le modèle de branches / déploiement n'était pas documenté. Objectif : solidifier la CI (court terme) et poser les conventions gitflow avant la mise en place du déploiement cloud (preprod VPS Hostinger, puis prod).

## Changements

1. **Back CI durcie** (`.github/workflows/ci.yml`) — passage de `mvn test` à `mvn verify` (build + tests + packaging du jar), upload du jar en artefact, et **filtrage par chemin** (`apps/back/**`) pour ne déclencher la CI back que si le back change.
2. **Front CI ajoutée** (`.github/workflows/front-ci.yml`) — `npm ci`, lint (non bloquant), tests unitaires headless (ChromeHeadless), build de production ; filtrée sur `apps/front/**`.
3. **Outillage qualité front** — ESLint (`angular-eslint`) et Prettier câblés, avec scripts `lint`, `format`, `format:check` ; config `eslint.config.js`, `.prettierrc.json`, `.prettierignore`.
4. **Correction d'un build cassé** — `dashboard-page.ts` importait un `NotificationsService` inexistant dans le dépôt (build de production front en échec, y compris sur `develop`). Import/injection retirés ; l'erreur de chargement des occurrences est désormais loguée via `console.error`.
5. **Documentation du gitflow** — nouvelle page de référence [`docs/gitflow.md`](../gitflow.md) : modèle de branches (`main`/`develop`/`feature`/`fix`/`hotfix`/`claude`, sans `release`), cycle de contribution, versionnage SemVer par tags, mapping CI et cible de déploiement (GHCR + SSH).

## Vérification

- Back : `mvn -B verify` → build + tests OK, jar `back-0.0.1-SNAPSHOT.jar` produit.
- Front : `npm run build --configuration production` → OK après correction de l'import mort ; `ng lint` → 6 écarts mineurs remontés (non bloquants).

## Hors périmètre (suite prévue)

- Dockerfiles de production (back, front) et workflow de build/push d'images vers GHCR.
- Migrations versionnées (Flyway) en remplacement de `ddl-auto: update`.
- Workflows de déploiement preprod (auto sur `develop`) et prod (sur tag, avec approbation).
- Bascule du lint front en bloquant une fois les règles arbitrées.
