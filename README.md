# AgoraPilot

AgoraPilot est un outil de planification et de publication automatisée de contenus, pensé pour une organisation qui souhaite préparer ses publications à l'avance et les diffuser à date fixe sur plusieurs canaux.

## Objectif du projet

- Centraliser la création de contenus (**Publications**), éventuellement regroupés en **Campagnes** limitées dans le temps.
- Planifier une ou plusieurs diffusions (**Occurrences**) pour chaque publication.
- Diffuser chaque occurrence sur un ou plusieurs canaux (**Livraisons**) :
  - **Facebook** (Page), via l'API Graph de Meta
  - **Intramuros** (canal de communication interne — à venir)
- Offrir un tableau de bord donnant une vue d'ensemble des publications à venir et de leur statut de diffusion.

## Stack technique

### Backend — `apps/back`
- Java 21 / Spring Boot 4.0.3 (Maven)
- Spring Data JPA (Hibernate, `ddl-auto: update`)
- Spring Security, Spring Validation, Spring WebMVC
- PostgreSQL (driver JDBC) + H2 pour les tests
- Lombok
- springdoc-openapi (Swagger UI)

### Frontend — `apps/front`
- Angular 20 (composants standalone, signals)
- TypeScript 5.8 (mode strict)
- Tailwind CSS 4
- RxJS
- Vitest (jsdom) pour les tests

### Infrastructure
- PostgreSQL 16 via Docker Compose
- CI GitHub Actions (tests backend uniquement pour l'instant)

## Structure du projet

```
apps/
├── back/   # API REST Spring Boot
│   └── src/main/java/fr/maximechazard/agorapilot/back/
│       ├── campaign/     # Campagnes
│       ├── publication/  # Publications, occurrences, livraisons
│       ├── publisher/    # Abstraction de diffusion + intégration Facebook
│       └── config/       # Sécurité, CORS, JPA, gestion des erreurs
└── front/  # SPA Angular
    └── src/app/
        ├── core/layout/       # Coquille applicative (sidebar, ...)
        └── features/
            ├── dashboard/     # Tableau de bord
            ├── occurrences/   # Occurrences planifiées
            └── publications/  # Création de publications
```

## Démarrage rapide

### Backend + base de données
```bash
docker compose up
```
Démarre PostgreSQL et l'API Spring Boot (port 8080). Nécessite un fichier `.env` à la racine (voir `docker-compose.yaml` et `apps/back/src/main/resources/application.yaml` pour les variables Facebook attendues).

### Frontend
```bash
cd apps/front
npm install
npm start
```
Démarre l'application Angular sur http://localhost:4200 (l'API est attendue sur http://localhost:8080/api).

## Todo / Roadmap

- [ ] Authentification et gestion des utilisateurs (aucune restriction d'accès actuellement)
- [ ] Déclenchement automatique des publications à l'heure planifiée (le `FacebookPublisher` existe et fonctionne, mais rien ne parcourt encore les occurrences dues pour l'appeler)
- [ ] Implémentation du canal Intramuros (pour l'instant seulement une valeur d'enum et des tokens de design)
- [x] Médiathèque : stockage sur volume, page dédiée, rattachement des visuels aux publications et diffusion des images sur Facebook (diffusion à valider en préproduction)
- [ ] CRUD complet des campagnes et publications (édition, suppression — seuls create/list existent)
- [ ] Connecter les KPIs et le panneau "prochaines publications" du dashboard à des données réelles
- [ ] Intégrer le formulaire de création de publication à une route de l'application
- [ ] Mettre en place une CI frontend (build, tests, lint)
- [ ] Ajouter un linter/formatter JS/TS (ESLint, Prettier)
- [ ] Migrations de base de données versionnées (Flyway/Liquibase) plutôt que `ddl-auto: update`
- [ ] Fournir un `.env.example` pour faciliter l'onboarding
- [ ] Étoffer la couverture de tests (seul un test de contexte Spring existe actuellement)
- [x] Dockerfiles de production pour le backend et le frontend (cf. [`docs/deploiement-preprod.md`](./docs/deploiement-preprod.md))
