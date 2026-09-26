# Diffusion planifiée — socle back (planification + ordonnanceur)

## Contexte

Le domaine décrit des occurrences comme des « diffusions planifiées », mais rien
ne les planifiait ni ne les diffusait :

- `PublicationOccurrenceService.create()` existait et n'était appelé par personne —
  `PublicationOccurrenceController` n'exposait que `GET /api/occurrences/weekly` ;
- aucun `@Scheduled` ne dépilait les occurrences dues. Le seul job du projet est
  celui du renouvellement du token Facebook.

Autrement dit, depuis
[la diffusion immédiate](./2026-08-22-publication-facebook-immediate.md) on savait
publier *maintenant* et afficher le calendrier de la semaine, mais une occurrence
`SCHEDULED` serait restée en attente indéfiniment. Cette tâche ferme la boucle,
côté back uniquement : l'interface de planification reste à faire.

## Changements

### Nouveaux chemins

```
POST /api/occurrences
{ "publicationId": 1, "scheduledAt": "2026-10-01T10:00:00", "channels": ["FACEBOOK"] }
```

| Code | Cas |
|---|---|
| `201` | occurrence planifiée ; le corps porte son `status` et ses livraisons `PENDING` |
| `400` | `scheduledAt` absente ou passée, `channels` vide, publication absente du corps |
| `404` | publication inexistante |
| `501` | canal sans publisher branché (Intramuros aujourd'hui) |

Rien n'est diffusé à la planification : l'occurrence naît `SCHEDULED` avec une
livraison `PENDING` par canal, et `PublicationOccurrenceScheduler` les sert le
moment venu (balayage toutes les minutes, réglable par
`agorapilot.scheduling.occurrences-delay` / `OCCURRENCES_SCHEDULER_DELAY`).

### Fichiers

| Fichier | Rôle |
|---|---|
| `publication/PublicationOccurrenceScheduler.java` | **nouveau** — balaie les occurrences en retard et délègue leur diffusion |
| `publication/PublicationOccurrenceController.java` | expose `POST /api/occurrences` |
| `publication/PublicationOccurrenceService.java` | `create()` renvoie un DTO et refuse un canal sans publisher ; `refreshStatus()` résout le statut d'une occurrence |
| `publication/PublicationDeliveryService.java` | `publishScheduledOccurrence()` : sert les livraisons `PENDING` d'une occurrence due |
| `publication/PublicationOccurrenceStatus.java` | nouveau statut `FAILED` |
| `publication/dtos/PublicationOccurrenceDTO.java` + son mapper | exposent `status`, que le front déclarait déjà sans le recevoir |
| `publication/requests/CreatePublicationOccurrenceRequest.java` | `@Future` sur `scheduledAt`, `@NotEmpty` sur `channels` |
| `apps/front/.../occurrence.model.ts` | le type `status` admet `FAILED` |

## Choix

**Un statut `FAILED` sur l'occurrence.** C'est ce qui la fait sortir de la file :
l'ordonnanceur retient les occurrences `SCHEDULED` dont l'heure est passée, et
une occurrence dont un canal a échoué serait revenue à chaque balayage,
indéfiniment. Le statut est désormais déduit des livraisons en un seul endroit
(`refreshStatus`), utilisé aussi bien par la diffusion immédiate que par
l'ordonnanceur : `PUBLISHED` quand tous les canaux sont servis, `FAILED` dès
qu'un canal a échoué, `SCHEDULED` tant qu'un canal reste `PENDING`.

**Aucune reprise automatique.** Seules les livraisons `PENDING` sont diffusées.
Une livraison `FAILED` n'est pas retentée : le canal distant peut avoir accepté
la publication avant de rompre la connexion, et une reprise automatique
publierait deux fois. La reprise restera un geste explicite (endpoint dédié, à
faire).

**Les canaux sans publisher sont refusés à la planification** (`501`) plutôt que
découverts trois jours plus tard. Le garde-fou existe quand même côté
ordonnanceur : une livraison dont le canal n'a pas de publisher est tracée
`FAILED` avec le motif, faute de quoi elle resterait `PENDING` pour toujours.

**Pas de diffusion concurrente.** L'ordonnanceur Spring n'exécute qu'une tâche à
la fois et `fixedDelay` compte à partir de la fin du balayage précédent : deux
passes ne peuvent pas se chevaucher et servir deux fois la même livraison, même
si un canal distant traîne. Ce raisonnement ne tient que pour une instance unique
du back — ce qui est le cas aujourd'hui.

**Un échec n'emporte pas le lot.** Chaque occurrence est traitée dans sa propre
transaction, et l'exception est capturée par occurrence : les suivantes sont déjà
en retard, elles partent quand même. Le dernier motif d'erreur reste lisible sur
le bean (`getLastError()`), comme pour le job du token Facebook.

## Migration à passer sur la préproduction

Hibernate dérive une contrainte `CHECK` de chaque enum, et `ddl-auto: update` ne
la met **pas** à jour. Sur une base déjà créée, la contrainte n'autorise que
`SCHEDULED` et `PUBLISHED` : la première écriture d'un `FAILED` échouerait. À
passer une fois sur la base de préproduction (cf.
[runbook](../deploiement-preprod.md#migration-ponctuelle--statut-failed-des-occurrences)) :

```sql
ALTER TABLE publication_occurrences DROP CONSTRAINT IF EXISTS publication_occurrences_status_check;
ALTER TABLE publication_occurrences ADD CONSTRAINT publication_occurrences_status_check
    CHECK (status IN ('SCHEDULED', 'PUBLISHED', 'FAILED'));
```

Un rappel de plus que Flyway est un prérequis avant la production.

## Vérification

```bash
cd apps/back && mvn -B verify   # 140 tests, BUILD SUCCESS
cd apps/front && npm test       # 31 fichiers, 269 tests
```

Le DDL PostgreSQL a été généré (`jakarta.persistence.schema-generation`) pour
constater la contrainte `check (status in ('SCHEDULED','PUBLISHED','FAILED'))`
et en déduire la migration ci-dessus.

Tests ajoutés ou repris :

- `PublicationOccurrenceServiceTest` (9) — planification d'une occurrence
  `SCHEDULED` portant une livraison `PENDING` par canal, aucune diffusion à la
  planification, publication inconnue, canal sans publisher ; résolution du
  statut vers `PUBLISHED`, `FAILED`, maintien en `SCHEDULED` tant qu'un canal
  attend, occurrence sans livraison, absence d'écriture inutile.
- `PublicationOccurrenceSchedulerTest` (4) — seules les occurrences `SCHEDULED`
  en retard sont retenues, rien à faire quand la file est vide, le lot continue
  après l'échec d'une occurrence, remise à zéro du dernier motif d'erreur.
- `PublicationDeliveryServiceTest` (12) — `publishScheduledOccurrence` sert les
  canaux `PENDING`, ne retente jamais un `FAILED`, ne republie pas un
  `PUBLISHED`, trace un canal sans publisher, sert les autres canaux malgré un
  échec, ignore une occurrence supprimée entre-temps.
- `PublicationOccurrenceControllerTest` (6) — contrat de `POST /api/occurrences`
  (`201`, `404`, `501`, `400` sur date passée et sur `channels` vide) et
  présence de `status` dans le calendrier hebdomadaire.

La diffusion réelle à l'heure dite n'a pas été exercée de bout en bout : elle
demande un token de page valide en base et une occurrence datée. Le chemin est
couvert unitairement de la requête HTTP jusqu'à l'appel du publisher.

## Reste à faire

- **Front** : planifier une diffusion depuis une publication, et afficher le
  statut de l'occurrence (dont `FAILED`) dans le calendrier.
- **Reprise d'une livraison en échec** : endpoint dédié, puisque l'ordonnanceur
  ne retente rien.
- **Flyway**, avant que les migrations manuelles ne se multiplient.
- **Canal Intramuros** : la planification répond `501` tant qu'aucun publisher
  n'est branché.
