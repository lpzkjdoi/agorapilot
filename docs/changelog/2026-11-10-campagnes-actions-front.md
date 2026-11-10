# Campagnes : modifier, clôturer, annuler et supprimer depuis la page

**Date** : 2026-11-10
**Périmètre** : `apps/front`
**Dépend de** : [fondations back](./2026-11-10-campagnes-fondations-back.md) (lot 1)

## Contexte

Lot 2 de la refonte des campagnes. Le back sait désormais modifier, clôturer,
annuler et archiver une campagne ; la page Campagnes, elle, restait en lecture
seule : aucun moyen visible de revenir sur une campagne créée.

## Changements

### Actions dans le détail

Sous le titre de la campagne sélectionnée, pour un compte qui a le droit
`campaigns:write` :

| Action | Proposée quand | Confirmation |
|---|---|---|
| **Modifier** | toujours | non : ouvre le formulaire |
| **Clôturer** | campagne en cours | « Sa date de fin passera à aujourd’hui. » |
| **Annuler la campagne** | brouillon, programmée ou en cours | « Les diffusions déjà programmées partiront quand même. » |
| **Supprimer** | toujours | « Ses publications sont conservées et redeviennent sans campagne. » |

La confirmation s'affiche dans le détail, comme la suppression d'une carte de
publication, plutôt qu'avec un `confirm()` natif. Changer de campagne
l'abandonne. Les boutons sont désactivés pendant la requête, et chaque issue
est notifiée. Une campagne supprimée quitte la liste et la suivante est
sélectionnée.

Sans le droit d'écriture, le détail ne montre aucune action.

### Un seul formulaire pour créer et modifier

`CreateCampaignModalComponent` devient `CampaignFormModalComponent`. Avec une
campagne en entrée, il est prérempli et titré « Modifier la campagne ». La date
de début n'y est pas bornée, pour qu'une campagne en cours garde sa date passée.
En création, le sélecteur commence toujours aujourd'hui. Dans les deux cas, une
date de fin antérieure au début est signalée avant l'envoi.

Lors d'une modification, un jour laissé tel quel garde son heure d'origine,
celle d'une clôture par exemple, au lieu d'être ramené à minuit.

### Service

`CampaignsService` gagne `updateCampaign`, `closeCampaign`, `cancelCampaign` et
`archiveCampaign`. `CreateCampaignRequest` devient `CampaignRequest`, partagé
par la création et la modification.

## Vérification

```bash
cd apps/front && npm test       # 62 fichiers, 613 tests
npx ng build                    # OK
```

J'ai aussi parcouru la page dans le navigateur (`ng serve`, avec une API
simulée faute de base locale) : modification préremplie puis enregistrée,
clôture d'une campagne en cours (le badge passe à « Terminée » et seules
Modifier et Supprimer restent), suppression confirmée (la campagne suivante est
sélectionnée). La console n'a montré aucune erreur liée aux campagnes.

Tests ajoutés :

- `CampaignDetailComponent` (10) : actions selon le statut et le droit,
  confirmation, « Retour », abandon au changement de campagne, désactivation.
- `CampaignFormModalComponent` (6) : préremplissage, titres, date de début non
  bornée en modification, période à l'envers.
- `CampaignsPageComponent` (8) : `PUT` en conservant l'heure, échec de
  modification, clôture, annulation, suppression, refus `409`.
- `CampaignsService` (4) : les quatre nouveaux appels.

## Reste à faire

- Lot 3 : planification des publications dans le détail.
- Lot 4 : calendrier de la campagne.
