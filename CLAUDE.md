# CLAUDE.md — Règles de fonctionnement AgoraPilot

Ce fichier est chargé automatiquement à chaque session. Il définit les règles à
respecter **systématiquement**, sans avoir besoin qu'on me les redemande.

## Workflow Git & PR

- **Toujours** développer sur une branche dédiée nommée `claude/<description-courte>`.
  Ne jamais committer directement sur `main`.
- **Ouvrir automatiquement une Pull Request en draft** vers `main` après le push,
  s'il n'en existe pas déjà une d'ouverte pour la branche.
- Messages de commit clairs et descriptifs (impératif, en français).

## Tests — systématiques

Toute évolution du code s'accompagne de tests. Ne jamais livrer de code non testé.

- **Backend** (`apps/back`) — Java 21 / Spring Boot, JUnit :
  ```bash
  cd apps/back && mvn -B test
  ```
  C'est ce que vérifie la CI (`.github/workflows/ci.yml`), donc les tests back
  doivent passer avant tout push.
- **Frontend** (`apps/front`) — Angular 20, Karma/Jasmine :
  ```bash
  cd apps/front && npm test        # ng test
  ```

## Documentation — systématique

Une entrée de changelog est ajoutée **à chaque tâche de développement significative** :

- Créer `docs/changelog/AAAA-MM-JJ-sujet.md` : contexte, changements, vérification
  effectuée, lien vers le commit.
- Mettre à jour le tableau récapitulatif dans `docs/README.md`.

## Stack technique (rappel)

- **Backend** `apps/back` : Java 21, Spring Boot 4, Spring Data JPA, Spring Security,
  PostgreSQL (H2 en test), Lombok, springdoc-openapi. Build : Maven (`./mvnw`).
- **Frontend** `apps/front` : Angular 20 (standalone, signals), TypeScript strict, RxJS.

## Domaine métier

Publications → Occurrences (diffusions planifiées) → Livraisons (par canal :
Facebook Page via Graph API ; Intramuros à venir). Campagnes = regroupement de
publications limité dans le temps.
