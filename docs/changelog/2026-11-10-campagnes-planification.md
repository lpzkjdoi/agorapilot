# Campagnes : suivre et piloter la planification de ses publications

**Date** : 2026-11-10
**Périmètre** : `apps/back` (un endpoint), `apps/front`
**Dépend de** : [actions de gestion](./2026-11-10-campagnes-actions-front.md) (lot 2), elle-même sur le lot 1

## Contexte

Lot 3 de la refonte des campagnes. Le détail d'une campagne listait le texte de
ses publications et les comptait par statut éditorial (vérifiées, brouillons),
sans rien dire de leur diffusion. Pour gérer les publications d'une campagne,
il fallait aussi passer par la page Publications.

## Changements

### Back : `GET /api/campaigns/{id}/occurrences`

L'endpoint renvoie toutes les diffusions des publications de la campagne,
passées comme à venir, dans l'ordre chronologique. Il ne prend pas de bornes de
dates, contrairement à `GET /api/occurrences?from&to` : une campagne peut durer
indéfiniment, et ses diffusions restent peu nombreuses. Les publications
supprimées (archivées) sont exclues. La réponse est `404` pour une campagne
inconnue ou archivée. Droit requis : `campaigns:read`.

### Front : la planification dans le détail

- **Compteurs** : Publications, Diffusions programmées, Publiées et En échec
  remplacent Vérifiées et Brouillons. Pendant le chargement des diffusions,
  seul le nombre de publications s'affiche. Si ce chargement échoue, un message
  le signale.
- **Pour chaque publication** :
  - la prochaine diffusion, avec sa date, son heure et son canal, et le nombre
    de diffusions qui suivent ;
  - ou « Aucune diffusion programmée » ;
  - le dernier résultat, « Publiée le… » ou « Échec le… ». Le motif de l'échec
    apparaît au survol.
- **Programmer**, sur une publication vérifiée (droit `occurrences:schedule`) :
  ouvre la modale de programmation de la page Publications.
- **Ajouter des publications** et **Retirer** (droit `publications:write`). La
  modale d'ajout liste les publications qui ne sont pas dans la campagne, avec
  une recherche et des cases à cocher. Celles d'une autre campagne y figurent
  avec la mention « Déplacée depuis « X » ».
- Les diffusions sont rechargées à chaque changement de campagne et après une
  programmation, un ajout ou un retrait. Une réponse arrivée après qu'on a
  changé de campagne est ignorée (`switchMap`).

`scheduledMessage`, le texte « Diffusion programmée le… », passe de la page
Publications à `occurrence.utils` pour servir aux deux pages.
`DELIVERY_CHANNEL_LABELS` rejoint `publication.model`.

## Vérification

```bash
cd apps/back && mvn -B verify   # 367 tests, BUILD SUCCESS
cd apps/front && npm test       # 64 fichiers, 642 tests
```

Parcours dans le navigateur (`ng serve`, avec une API simulée) :

- planification affichée, dont un échec et une diffusion à venir ;
- ajout d'une publication par la modale ;
- programmation de cette publication, dont la ligne affiche ensuite sa
  prochaine diffusion et dont les compteurs s'actualisent ;
- retrait de la publication.

Tests ajoutés :

- Back :
  - `CampaignServiceTest.Occurrences` (2) et `CampaignControllerTest.Occurrences` (2) ;
  - `OccurrenceSlotsPersistenceTest`, qui vérifie l'ordre chronologique et
    l'exclusion des publications supprimées et de celles hors campagne ;
  - `EndpointPermissionsTest`.
- Front :
  - `campaign-planning` (4) ;
  - `AddPublicationsModalComponent` (7) ;
  - `CampaignDetailComponent`, planification (7) ;
  - `CampaignsPageComponent`, publications de la campagne (7) ;
  - `scheduledMessage` (3) et le nouvel appel du service.

## Limites

- Rien ne lie encore la date d'une diffusion à la période de la campagne. Le
  calendrier du lot 4 pourra signaler les diffusions hors période.
- Le contenu d'une publication n'est pas un lien vers la page Publications, qui
  n'a pas d'adresse propre par publication.
