# Annuler ou clôturer une campagne déprogramme ses diffusions

**Date** : 2026-11-10
**Périmètre** : `apps/back`, `apps/front`
**Dépend de** : [calendrier de campagne](./2026-11-10-campagnes-calendrier.md) (lot 4) et les lots précédents

## Contexte

Jusqu'ici, annuler ou clôturer une campagne laissait partir les diffusions
déjà programmées de ses publications ; la confirmation d'annulation le
signalait. Décision de Maxime : une campagne annulée ou clôturée ne doit plus
rien diffuser.

## Changements

### Back

`PublicationOccurrenceService.cancelScheduledOfCampaign(campaignId)` supprime
toutes les diffusions **programmées** des publications de la campagne
(publications supprimées exceptées) :

- les diffusions publiées ou en échec restent : elles forment l'historique de
  la campagne ;
- chaque diffusion annulée est inscrite au journal (`OCCURRENCE_CANCELLED`,
  « déprogrammée avec sa campagne ») ;
- les jours touchés sont répartis à nouveau, comme pour une annulation unitaire ;
- **tout ou rien**, comme pour la suppression d'une publication : si une seule
  diffusion est en cours ou imminente (moins de 5 minutes), rien n'est
  déprogrammé et l'annulation ou la clôture est refusée (`409`).

`CampaignService.cancel` et `CampaignService.close` l'appellent avant de
changer le statut de la campagne. Les refus existants arrivent avant et ne
touchent pas aux diffusions : campagne terminée ou déjà annulée, ou campagne
pas encore commencée pour la clôture.

### Front

- Les deux confirmations annoncent la déprogrammation.
- La notification de succès donne le nombre de diffusions déprogrammées
  (« Campagne clôturée : 1 diffusion déprogrammée. »), et la planification est
  rechargée.
- En cas de refus, la notification reprend le motif du back, par exemple une
  diffusion imminente.

## Vérification

```bash
cd apps/back && mvn -B verify   # 369 tests, BUILD SUCCESS
cd apps/front && npm test       # 65 fichiers, 661 tests
```

Tests ajoutés :

- `OccurrenceSlotsPersistenceTest` (2) :
  - l'annulation supprime les diffusions de la campagne, garde celle d'une
    publication hors campagne le même jour et répartit ce jour à nouveau ;
  - une diffusion imminente bloque tout.
- `CampaignServiceTest.Cancel` et `.Close` (2 chacun, plus une vérification
  ajoutée à chacun) :
  - appel de la déprogrammation ;
  - campagne inchangée si elle échoue ;
  - aucune déprogrammation sur un refus.
- `CampaignsPageComponent` : notification avec et sans diffusions, pour
  l'annulation comme pour la clôture, et motif du `409`.
- `CampaignDetailComponent` : textes des deux confirmations.
