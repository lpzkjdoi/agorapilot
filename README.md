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
- Spring Data JPA (Hibernate), schéma porté par des migrations Flyway
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

### À faire

- [ ] Campagnes : bouton « Clôturer » côté front (l'endpoint existe), annulation (`CANCELED` existe mais rien ne le pose), modification et archivage
- [ ] Créneaux de diffusion : imposer un écart minimal entre une diffusion à heure fixe (épinglée) et les diffusions réparties automatiquement — aujourd'hui une épinglée à 17:30 peut côtoyer une automatique à 17:25
- [ ] Suspension Facebook — limites connues de la première version (cf. [changelog](docs/changelog/2026-10-16-suspension-canal-facebook.md#limites-connues)) :
  - [ ] Levée uniquement manuelle : un nouveau jeton posé par `POST /admin/facebook/bootstrap` ne lève pas une suspension pour jeton invalide (code 190), il faut aussi cliquer « Reprendre les diffusions » — la lever automatiquement après un bootstrap réussi
  - [ ] Diffusions retenues : passée leur heure, elles restent « programmées » dans le calendrier et ne peuvent être ni déplacées ni annulées tant qu'elles ne sont pas tranchées (levée, ou échec au-delà du retard maximal) — les signaler comme « en attente » et permettre de les annuler
  - [ ] Pas d'alerte hors de l'application : la suspension ne se voit qu'en ouvrant AgoraPilot — prévenir par mail les comptes `channels:manage` (les comptes existent, mais sans adresse e-mail : à ajouter, avec un serveur d'envoi)
- [ ] Reprendre par glisser-déposer une diffusion en échec vers un autre jour du calendrier
- [ ] Tableau de bord : rendre cliquables le KPI « Diffusions en échec » (vers les diffusions concernées, ce qui demande un filtre par statut dans le calendrier) et les jours de « Cette semaine », dont le chevron ne mène nulle part
- [ ] Restaurer une publication supprimée qui a été archivée pour son historique (aucune interface aujourd'hui)
- [ ] Journal de sécurité : filtres (type, compte, période) et pagination — la page Comptes n'en montre que les 50 derniers événements
- [ ] Implémentation du canal Intramuros (pour l'instant seulement une valeur d'enum et des tokens de design)
- [ ] Appliquer Prettier au code front et le vérifier en CI (installé, scripts `format` / `format:check` présents, mais 136 fichiers non conformes)
- [ ] Fournir un `.env.example` pour le développement local (seul `.env.preprod.example` existe), avec `AGORAPILOT_ADMIN_USERNAME` / `AGORAPILOT_ADMIN_PASSWORD` : sans eux, une base locale vide n'a aucun compte
- [ ] Fonctionnalités IA (génération de posts, campagnes, résumés, traitement des mails) — voir [la proposition](docs/proposals/2026-09-24-ai-features-and-email-intake.md)

### Fait

- [x] **Comptes applicatifs et droits** (2026-10-24, cf. [proposition](docs/proposals/2026-10-24-comptes-et-droits.md)) : une personne = un compte, refus par défaut et droits fins `ressource:action` par endpoint, session côté serveur avec CSRF, premier administrateur au démarrage, page « Comptes » (profils, désactivation, mot de passe provisoire), seules les publications vérifiées partent, actions masquées selon les droits, auteurs des publications et diffusions, journal de sécurité et des actions sensibles
- [x] Migrations de base de données versionnées (Flyway) : schéma de départ en `V1`, Hibernate en `validate`, préproduction inscrite d'office en version 1
- [x] CRUD complet des publications : modification (texte, statut) et suppression, qui annule les diffusions programmées et archive une publication déjà diffusée
- [x] Tableau de bord branché sur des données réelles (`GET /api/dashboard`) : campagnes actives, diffusions de la semaine, brouillons, échecs, et les cinq prochaines diffusions
- [x] Suspension du canal Facebook quand le compte est bloqué (codes 368, 190) : plus d'appel tant que la suspension n'est pas levée, bandeau global avec bouton « Reprendre les diffusions »
- [x] Reprise d'une diffusion en échec depuis le calendrier, et motif lisible des refus de Facebook
- [x] Écran de programmation : bouton « Programmer » sur la carte, page Calendrier avec glisser-déposer, modification et annulation d'une diffusion
- [x] Déclenchement automatique des publications à l'heure planifiée (`PublicationOccurrenceScheduler`, balayage chaque minute)
- [x] Médiathèque : stockage sur volume, page dédiée, rattachement des visuels aux publications et diffusion des images sur Facebook (diffusion à valider en préproduction)
- [x] Formulaire de création de publication intégré à la page Publications (modale)
- [x] CI front complète : build, tests et lint ESLint (`front-ci.yml`)
- [x] Couverture de tests back (JUnit) et front (Vitest), exigée pour toute évolution (cf. `CLAUDE.md`)
- [x] Dockerfiles de production pour le backend et le frontend (cf. [`docs/deploiement-preprod.md`](./docs/deploiement-preprod.md))
