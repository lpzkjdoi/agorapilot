# Programmation par fréquence : le back

## Contexte

Une publication se programmait à une ou plusieurs dates, saisies une à une.
La roadmap prévoyait de la répéter selon une fréquence (« chaque lundi »,
« tous les 3 mois »…), sur le modèle des Rappels iOS/macOS, avec des choix
déjà tranchés :
- règle au format RFC 5545 ;
- un 31 absent du mois tombe le dernier jour du mois ;
- fin de la série : jamais, à une date, ou après N diffusions ;
- diffusions créées à l'avance par un job quotidien ;
- lien de chaque diffusion vers sa série et sa date d'origine.

Ce changement livre le back. La modale « Programmer » viendra dans une
seconde PR.

## Changements

### Règle de répétition (`RecurrenceRule`)

- Lecture et écriture d'une `RRULE`, sous une forme normalisée.
- Sous-ensemble accepté :
  - `FREQ` : `DAILY`, `WEEKLY`, `MONTHLY` ou `YEARLY` ;
  - `INTERVAL` : « tous les N » ;
  - `BYDAY` : jours de la semaine, ou jour ordinal en mensuel et annuel
    (`1MO` pour le premier lundi, `-1FR` pour le dernier vendredi) ;
  - `BYMONTHDAY` et `BYMONTH` ;
  - `COUNT` **ou** `UNTIL` pour la fin.
- Tout le reste est refusé avec un motif lisible (400).
- **Écart voulu à la norme** : un jour 29 à 31 absent du mois tombe le dernier
  jour du mois, au lieu d'être sauté. Un « 5e lundi » absent, lui, est sauté.
- Tous les préréglages prévus s'expriment avec ce sous-ensemble :

| Préréglage | Règle |
|---|---|
| Chaque jour | `FREQ=DAILY` |
| Jours ouvrés | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` |
| Week-ends | `FREQ=WEEKLY;BYDAY=SA,SU` |
| Chaque semaine | `FREQ=WEEKLY` |
| Toutes les 2 semaines | `FREQ=WEEKLY;INTERVAL=2` |
| Chaque mois | `FREQ=MONTHLY` |
| Tous les 3 mois | `FREQ=MONTHLY;INTERVAL=3` |
| Tous les 6 mois | `FREQ=MONTHLY;INTERVAL=6` |
| Chaque année | `FREQ=YEARLY` |

### Série (`OccurrenceSeries`, migration `V7`)

- **Contenu** : la publication, la règle, le jour de départ, l'heure (fixe, ou
  nulle pour une heure automatique), les canaux et le statut (`ACTIVE`,
  `COMPLETED`, `STOPPED`, avec le motif de l'arrêt).
- **Curseur `generated_through`** : les dates jusqu'à ce jour ont été traitées.
  Une diffusion de la série déplacée ou annulée à la main n'est donc **jamais
  recréée**.
- Chaque diffusion générée porte `series_id` et `series_date`, sa date
  d'origine. `PublicationOccurrenceDTO` expose `seriesId`.
- Deux nouveaux événements au journal : `SERIES_CREATED` et `SERIES_STOPPED`.
  Leurs libellés front sont ajoutés.

### Génération

- **Quand** : à la création de la série, puis chaque nuit à 00:05 (réglable
  par `RECURRENCE_CRON`).
- **Jusqu'où** : jusqu'à 62 jours devant, et au plus 31 diffusions à venir par
  série (réglables par `RECURRENCE_HORIZON_DAYS` et
  `RECURRENCE_HORIZON_COUNT`). Une série quotidienne est donc limitée par le
  nombre, une série annuelle par les jours.
- **Une diffusion de série est une diffusion ordinaire** : même répartition
  automatique dans la fenêtre du jour, même épinglage pour une heure fixe.
  Elle se déplace, s'annule et se reprend comme les autres.
- **Dates impossibles ou manquées** : une date qui ne peut plus accueillir de
  diffusion (fenêtre du jour close, heure fixe passée) est écartée sans faire
  échouer le job. Les dates manquées pendant un arrêt du back ne sont pas
  rattrapées.
- **Fin** : une règle bornée passe `COMPLETED` une fois toutes ses dates
  traitées.
- **Une transaction par série** au job de nuit : une série en erreur
  n'empêche pas les autres de produire.

### Arrêt des séries

- **À la main** (`DELETE /api/occurrence-series/{id}`) : les diffusions encore
  programmées sont annulées et les jours touchés répartis à nouveau. C'est
  tout ou rien, comme pour une campagne : la demande est refusée si une
  diffusion est imminente.
- **Campagne clôturée ou annulée** : ses séries s'arrêtent avec ses diffusions
  à venir.
- **Publication supprimée** : ses séries s'arrêtent si elle est gardée comme
  historique, et disparaissent avec elle sinon.
- **Publication repassée en brouillon** : la série attend sans rien créer, puis
  reprend quand la publication est de nouveau vérifiée.

### API

| Méthode | Chemin | Droit |
|---|---|---|
| `POST` | `/api/occurrence-series` | `occurrences:schedule` |
| `GET` | `/api/occurrence-series?publicationId=` | `publications:read` |
| `DELETE` | `/api/occurrence-series/{id}` | `occurrences:schedule` |

Corps du `POST` : `publicationId`, `channels`, `rule`, `startDate` et `time`
(facultatif). Il est refusé si :
- le départ est dans le passé ;
- la fin précède le départ ;
- la règle ne produit aucune date dans les cinq ans ;
- la publication n'est pas vérifiée ;
- un canal n'a pas de publisher.

## Limites et reste à faire

- **Front** : la modale « Programmer » (préréglages, « Personnaliser », fin de
  répétition par défaut à la fin de la campagne) et l'affichage des séries
  (badge dans le calendrier, liste et arrêt). Ce sera la prochaine PR.
- **Alerte quand une publication repasse en brouillon** : aucune notification
  dédiée. Les diffusions déjà créées échouent à leur heure avec le motif
  habituel (« n'est plus vérifiée ») et la notification d'échec existante.
- Les diffusions d'une série gardent le texte de la publication au moment de
  leur départ, comme toute diffusion.

## Vérification

`mvn -B verify` vert, dont :
- `RecurrenceRuleTest` (33 cas) : tous les préréglages, le 31 absent, les
  jours ordinaux, le 5e lundi sauté, `COUNT` et `UNTIL`, la normalisation et
  les refus ;
- `OccurrenceSeriesPersistenceTest` (11 cas, H2 et migration `V7`) :
  - horizon en jours et en nombre, prolongation au fil des jours ;
  - heure fixe épinglée ;
  - pas de recréation après une annulation ou un déplacement ;
  - dates manquées écartées ;
  - fin `COMPLETED` ;
  - arrêt à la main, avec la campagne, ou à la suppression de la publication ;
  - attente en brouillon ;
  - refus d'un départ passé ou d'une fin avant le départ ;
- `OccurrenceSeriesControllerTest` et `EndpointPermissionsTest` : contrat HTTP
  et droits.

Front : `npm test` (675) et lint OK, seuls les libellés du journal ont changé.

## Commit

_voir PR_
