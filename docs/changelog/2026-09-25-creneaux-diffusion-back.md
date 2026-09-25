# Créneaux de diffusion — socle back

## Contexte

Programmer une diffusion demandait jusqu'ici une date **et** une heure précises
(`POST /api/occurrences` avec `scheduledAt`). À l'usage, c'est lourd : on veut
choisir le jour et laisser l'application choisir l'heure. Elle doit alors
publier quand les abonnés sont disponibles (en fin d'après-midi et en soirée),
jamais au milieu de la nuit, et fonctionner aussi bien avec 0 qu'avec 10
publications dans la journée.

Décisions prises avec Maxime :

- le moteur de diffusion (ordonnanceur à la minute, cf.
  [fiabilisation](./2026-09-25-fiabiliser-ordonnanceur.md)) est conservé tel quel ;
  seul le choix de l'heure change ;
- la fenêtre de publication est réglable, **15:00 – 22:00** par défaut ;
- les créneaux ne concernent que Facebook ;
- programmer pour aujourd'hui une fois la fenêtre close est **refusé** ;
- le jour et l'heure restent modifiables (glisser-déposer dans le calendrier, à
  venir côté front) ;
- l'écart minimal entre une diffusion à heure fixe et les automatiques n'est pas
  géré : il est noté dans la roadmap du README.

## Répartition

Les diffusions automatiques Facebook d'un jour occupent chacune le **milieu d'une
part égale** de la fenêtre (`SlotPlanner`), avec des horaires arrondis à 5 minutes :

| Diffusions ce jour-là | Horaires (fenêtre 15:00–22:00) |
|---|---|
| 1 | 18:30 |
| 2 | 16:45 · 20:15 |
| 3 | 16:10 · 18:30 · 20:50 |
| 5 | 15:40 · 17:05 · 18:30 · 19:55 · 21:20 |
| 10 | environ toutes les 42 min, de 15:20 à 21:40 |

Aucune diffusion ne sort de la fenêtre, quel que soit leur nombre, et l'écart
entre deux posts est toujours le plus grand possible.

La répartition d'un jour est **recalculée** à chaque ajout, annulation ou
déplacement ce jour-là. Un déplacement recalcule le jour quitté et le jour
d'arrivée. L'ordre relatif est conservé : une nouvelle venue prend le dernier
créneau. Ne bougent que les occurrences :

- encore `SCHEDULED` : ce qui est publié ou en échec reste en place ;
- **non épinglées** : une heure fixée à la main ne bouge jamais ;
- au-delà d'un **horizon figé de 5 minutes**. En deçà, l'ordonnanceur peut
  prendre l'occurrence en charge à tout instant, et la déplacer serait une
  course. C'est aussi pourquoi une occurrence imminente n'est plus modifiable.

Pour aujourd'hui, la répartition se fait sur ce qui reste de la fenêtre. À 18:32,
il reste 18:40 – 22:00.

## API

### Programmer — `POST /api/occurrences`

Deux formes, exclusives l'une de l'autre (sinon `400`) :

```json
{ "publicationId": 1, "date": "2026-10-03", "channels": ["FACEBOOK"] }
{ "publicationId": 1, "scheduledAt": "2026-10-03T17:30:00", "channels": ["FACEBOOK"] }
```

La première forme est automatique. La seconde fixe une heure et l'**épingle**.
C'est le comportement d'avant cette tâche, désormais marqué `pinned`. Une date
passée, ou le jour même après la fenêtre, donne `400` avec le motif.

### Déplacer, fixer l'heure, rendre automatique — `PUT /api/occurrences/{id}/schedule`

```json
{ "date": "2026-10-04" }                  // jour changé, heure automatique
{ "date": "2026-10-04", "time": "18:30" } // heure fixée (épinglée)
```

Un glisser-déposer d'une diffusion épinglée vers un autre jour renvoie son heure
actuelle dans `time`, pour la conserver. Réponses : `404` si l'occurrence est
inconnue, `409` si elle est déjà traitée ou imminente, `400` pour une heure
passée ou une fenêtre close.

### Annuler — `DELETE /api/occurrences/{id}`

`204`. Les autres diffusions du jour se répartissent à nouveau. Réponses : `404`,
ou `409` comme ci-dessus.

### Lister une période — `GET /api/occurrences?from=2026-10-01&to=2026-11-01`

Intervalle `[from, to)` de 93 jours au plus, trié par ordre chronologique. Il est
prévu pour le calendrier du mois.

Toutes les occurrences exposent désormais `pinned`.

## Fichiers

| Fichier | Rôle |
|---|---|
| `publication/SlotPlanner.java` | **nouveau** : calcul des créneaux, arrondis |
| `publication/PublicationOccurrenceService.java` | programmation par jour, `reschedule`, `cancel`, `findBetween`, répartition |
| `publication/PublicationOccurrenceController.java` | `GET ?from&to`, `PUT /{id}/schedule`, `DELETE /{id}` |
| `publication/PublicationOccurrence.java` | colonne `pinned` (`default false`) |
| `publication/SchedulingProperties.java` | `windowStart` / `windowEnd`, refus au démarrage d'une fenêtre inversée |
| `publication/requests/*` | `date` xor `scheduledAt` ; nouvelle `RescheduleOccurrenceRequest` |
| `publication/exceptions/*` + `GlobalExceptionHandler` | `InvalidScheduleException` (400), `OccurrenceNotFoundException` (404), `OccurrenceNotModifiableException` (409) |
| `config/AppConfig.java` | bean `Clock`, pour figer l'heure en test |
| `SchedulerAdminController` | expose aussi la fenêtre |
| `docker-compose.preprod.yaml`, `.env.preprod.example` | `PUBLICATION_WINDOW_START` / `_END`, avec valeurs par défaut |
| front `occurrence.model.ts` | champ `pinned` |

## Préproduction

**Aucune migration manuelle.** La colonne `pinned` est ajoutée par
`ddl-auto: update` avec `default false`, visible dans le DDL généré en test
(`pinned boolean default false not null`), donc sans erreur sur une table déjà
peuplée. Les diffusions existantes deviennent « non épinglées », ce qui est sans
effet puisqu'elles sont toutes publiées. Sans variable
`PUBLICATION_WINDOW_*`, la fenêtre est 15:00 – 22:00.

## Vérification

```bash
cd apps/back && mvn -B verify   # 234 tests, BUILD SUCCESS
cd apps/front && npm test       # 36 fichiers, 317 tests
```

- `SlotPlannerTest` (**nouveau**, 9) : horaires attendus pour 0, 1, 2, 3, 5 et 10
  diffusions, jamais hors fenêtre même avec 200, pas de 5 minutes, fenêtre
  partielle, fenêtre vide refusée.
- `OccurrenceSlotsPersistenceTest` (**nouveau**, 16, H2 et horloge pilotable) :
  - liaison de la fenêtre par défaut depuis la configuration ;
  - répartition recalculée à chaque ajout, en conservant l'ordre ;
  - jours voisins intacts ;
  - épinglée fixe ;
  - diffusion publiée ignorée ;
  - trou comblé après annulation ;
  - déplacement qui recalcule les deux jours ;
  - épinglage et retour à l'automatique ;
  - reste de la fenêtre du jour, et refus après sa fermeture ;
  - diffusion imminente ni déplacée ni modifiable ;
  - diffusion servie non modifiable ;
  - heure passée refusée ;
  - liste d'une période.
- `PublicationOccurrenceControllerTest` : contrat HTTP des quatre routes
  (201/400 date xor heure, 400 avec motif, 200/400 période, 200/400/404/409
  déplacement, 204/409 annulation).
- `PublicationOccurrenceServiceTest` : heure précise épinglée, refus d'un jour
  passé et d'une fenêtre close.
- `PublicationOccurrenceMapperTest`, `SchedulerAdminControllerTest` : `pinned`
  et fenêtre exposés.

## Limites connues

- **Écart minimal** entre une épinglée et les automatiques : non géré (roadmap).
- **Deux modifications simultanées du même jour** (deux onglets) peuvent se
  répartir sur des lectures croisées. Le cas est improbable pour une seule
  personne ; un verrou par jour le réglerait.
- **Intramuros** : une occurrence sans canal Facebook ne serait pas répartie. Elle
  ne peut de toute façon pas être créée aujourd'hui (`501`).

## Reste à faire

- Front : bouton « Programmer » (jour et canaux), page calendrier avec
  glisser-déposer entre les jours, champ heure pour épingler, annulation.
