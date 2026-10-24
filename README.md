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

### À faire

- [ ] **Gestion des utilisateurs, de bout en bout** — aujourd'hui toute l'API est ouverte (`permitAll`) et l'accès ne repose que sur le réseau (préprod derrière WireGuard) :
  - [ ] Décider du modèle d'accès : comptes applicatifs, VPN seul, ou les deux (décision en attente)
  - [ ] Back : entité utilisateur (nom, e-mail, rôle, actif), mots de passe hachés, authentification Spring Security avec session ou jeton, rôles (au moins administrateur / rédacteur)
  - [ ] Back : protéger `/api/**` et les endpoints `/admin/**` (Facebook, ordonnanceur), en remplaçant la chaîne de filtres permissive
  - [ ] Back : création, désactivation et réinitialisation du mot de passe des comptes (endpoints administrateur), premier compte au démarrage
  - [ ] Front : page de connexion, garde de routes, intercepteur HTTP (jeton ou cookie, redirection sur `401`), déconnexion
  - [ ] Front : utilisateur connecté dans le bandeau, page d'administration des comptes
  - [ ] Traçabilité : auteur et dernier modificateur des publications, auteur d'une programmation, d'une reprise ou d'une levée de suspension
  - [ ] Tests à chaque étape (back et front) et documentation de mise en service en préprod
- [ ] Migrations de base de données versionnées (Flyway) plutôt que `ddl-auto: update` — deux `ALTER TABLE` manuels de contraintes ont déjà été nécessaires en septembre, et chaque nouvelle valeur d'enum en ajoute un
- [ ] Tableau de bord branché sur des données réelles : les KPIs sont codés en dur (4, 6, 34, 48) et le panneau « Prochaines publications » est affiché sans données ; y montrer aussi les statuts, échecs compris
- [ ] Campagnes : bouton « Clôturer » côté front (l'endpoint existe), annulation (`CANCELED` existe mais rien ne le pose), modification et archivage
- [ ] CRUD complet des publications : modification et suppression (seuls création, liste, programmation et rattachements existent)
- [ ] Créneaux de diffusion : imposer un écart minimal entre une diffusion à heure fixe (épinglée) et les diffusions réparties automatiquement — aujourd'hui une épinglée à 17:30 peut côtoyer une automatique à 17:25
- [ ] Suspension Facebook — limites connues de la première version (cf. [changelog](docs/changelog/2026-10-16-suspension-canal-facebook.md#limites-connues)) :
  - [ ] Levée uniquement manuelle : un nouveau jeton posé par `POST /admin/facebook/bootstrap` ne lève pas une suspension pour jeton invalide (code 190), il faut aussi cliquer « Reprendre les diffusions » — la lever automatiquement après un bootstrap réussi
  - [ ] Diffusions retenues : passée leur heure, elles restent « programmées » dans le calendrier et ne peuvent être ni déplacées ni annulées tant qu'elles ne sont pas tranchées (levée, ou échec au-delà du retard maximal) — les signaler comme « en attente » et permettre de les annuler
  - [ ] Pas d'alerte hors de l'application : la suspension ne se voit qu'en ouvrant AgoraPilot — prévenir par mail (après la gestion des utilisateurs, pour les destinataires)
- [ ] Reprendre par glisser-déposer une diffusion en échec vers un autre jour du calendrier
- [ ] Implémentation du canal Intramuros (pour l'instant seulement une valeur d'enum et des tokens de design)
- [ ] Appliquer Prettier au code front et le vérifier en CI (installé, scripts `format` / `format:check` présents, mais 136 fichiers non conformes)
- [ ] Fournir un `.env.example` pour le développement local (seul `.env.preprod.example` existe)
- [ ] Fonctionnalités IA (génération de posts, campagnes, résumés, traitement des mails) — voir [la proposition](docs/proposals/2026-09-24-ai-features-and-email-intake.md)

### Fait

- [x] Suspension du canal Facebook quand le compte est bloqué (codes 368, 190) : plus d'appel tant que la suspension n'est pas levée, bandeau global avec bouton « Reprendre les diffusions »
- [x] Reprise d'une diffusion en échec depuis le calendrier, et motif lisible des refus de Facebook
- [x] Écran de programmation : bouton « Programmer » sur la carte, page Calendrier avec glisser-déposer, modification et annulation d'une diffusion
- [x] Déclenchement automatique des publications à l'heure planifiée (`PublicationOccurrenceScheduler`, balayage chaque minute)
- [x] Médiathèque : stockage sur volume, page dédiée, rattachement des visuels aux publications et diffusion des images sur Facebook (diffusion à valider en préproduction)
- [x] Formulaire de création de publication intégré à la page Publications (modale)
- [x] CI front complète : build, tests et lint ESLint (`front-ci.yml`)
- [x] Couverture de tests back (JUnit) et front (Vitest), exigée pour toute évolution (cf. `CLAUDE.md`)
- [x] Dockerfiles de production pour le backend et le frontend (cf. [`docs/deploiement-preprod.md`](./docs/deploiement-preprod.md))
