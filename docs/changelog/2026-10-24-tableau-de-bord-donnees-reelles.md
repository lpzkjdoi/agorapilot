# Tableau de bord branché sur des données réelles

## Contexte

Les quatre KPIs du tableau de bord étaient codés en dur (4, 6, 34, 48) et le
panneau « Publications à venir » n'était alimenté par rien : il affichait
toujours « Aucune publication prévue ». Deux des quatre compteurs ne
correspondaient d'ailleurs à aucune notion du domaine (« Rappels prévus »), ou
à une notion floue (« Publications en attente »). La roadmap demandait aussi
d'y faire apparaître les échecs.

## Changements

### Back — `GET /api/dashboard`

Nouveau paquet `dashboard` : `DashboardController`, `DashboardService`,
`DashboardDTO`. Une seule requête renvoie :

| Champ | Définition |
|---|---|
| `activeCampaigns` | Campagnes en cours : date de début atteinte, date de fin absente ou future, ni clôturées, ni annulées, ni archivées. |
| `scheduledThisWeek` | Diffusions encore `SCHEDULED` d'aujourd'hui minuit à J+7 minuit, la période du calendrier « Cette semaine ». |
| `draftPublications` | Publications en brouillon (`DRAFT`), non archivées. |
| `failedOccurrences` | Diffusions `FAILED`, quelle que soit leur date : elles attendent une reprise. |
| `upcomingOccurrences` | Les cinq prochaines diffusions `SCHEDULED` à partir de maintenant, la plus proche d'abord. |

Les campagnes actives sont comptées **sur les dates**, pas sur le statut stocké :
rien ne fait passer une campagne `SCHEDULED` à `ACTIVE` quand sa date de début
arrive, ni une campagne `ACTIVE` à `COMPLETED` à sa date de fin.

Une diffusion dont l'heure est passée mais qui reste `SCHEDULED` (canal suspendu,
ordonnanceur en retard) compte dans la semaine, mais ne figure plus parmi les
« prochaines ».

Nouvelles requêtes dérivées sur `PublicationOccurrenceRepository` et
`PublicationRepository`, requête JPQL `countActive` sur `CampaignRepository`.

### Front

- `DashboardService` et le modèle `DashboardSummary`.
- KPIs renommés pour coller aux données : « Campagnes actives », « Diffusions
  programmées sur 7 jours », « Publications à vérifier », « Diffusions en
  échec » (à la place de « Rappels prévus »), cette dernière valeur en rouge
  quand elle est non nulle.
- `DashboardKpiComponent` : valeur `null` affichée « – » pendant le chargement
  et en cas d'erreur (plutôt que des zéros trompeurs), entrée `alert`.
- « Publications à venir » : cartes calquées sur la maquette (bloc date, texte
  sur deux lignes, badges canal et statut, jour et heure), note « Chargement… »
  au lieu de « Aucune publication prévue » pendant l'appel, « Voir tout » mène au
  calendrier.

## Vérification

- `mvn -B verify` : 284 tests, dont `DashboardPersistenceTest` (7, contre H2 avec
  une horloge figée : ce qui compte et ce qui ne compte pas pour chaque
  compteur, ordre et limite de la liste) et `DashboardControllerTest`.
- `npm test` : 446 tests (service, KPI, panneau, carte, page, routes) ;
  `ng lint` et `ng build` sans erreur.
- Vu dans le navigateur contre une API bouchonnée : compteurs, échec en rouge,
  cinq cartes dont une sur deux canaux et un texte long tronqué.

## Reste à faire

- Rendre le KPI « Diffusions en échec » cliquable vers les diffusions concernées
  (le calendrier ne sait pas encore filtrer par statut).
- Le chevron des jours de « Cette semaine » ne mène encore nulle part.
