# Clôturer une campagne

**Date** : 2026-09-22
**Périmètre** : `apps/back`

## Contexte

Deux demandes : vérifier qu'une campagne peut ne pas avoir de date de fin — être
« à durée indéterminée » — et prévoir un endpoint de clôture d'une campagne
commencée, qu'un bouton « Clôturer » appellera plus tard.

La première est confirmée : `end_date` est nullable, rien n'oblige à la
renseigner, et le statut ne se dérive que de la date de début. Un test de
persistance l'atteste désormais, de même que le cas d'une campagne sans aucune
date.

La seconde a mis au jour un défaut qui la rendait impossible.

## Le défaut : une campagne commencée n'était plus modifiable

L'entité `Campaign` portait `@FutureOrPresent` sur `startDate` et `@Future` sur
`endDate`. Ces contraintes décrivent une **règle de saisie**, mais posées sur
une entité persistante elles sont revérifiées par Hibernate **à chaque update** —
or une campagne vieillit : sa date de début finit par appartenir au passé.

Conséquence, prouvée par `CampaignPersistenceTest` avant correction :

- toute campagne dont la date de début est passée — donc toute campagne en
  cours — levait `ConstraintViolationException` à la moindre modification ;
- écrire une date de fin au présent, ce que fait précisément une clôture, était
  refusé par `@Future`.

Les deux annotations sont retirées de l'entité. Elles restent sur
`CreateCampaignRequest`, où elles ont leur sens : c'est à la création qu'on
exige une date future.

Le défaut n'avait jamais frappé parce qu'aucun écrit ne visait une campagne
existante : seules la création et la lecture étaient exposées.

## L'endpoint

`POST /api/campaigns/{id}/closure` — un sous-chemin nommé plutôt qu'une écriture
libre du statut : la clôture est une transition métier, pas un champ à poser.

- `200` avec la campagne : statut `COMPLETED`, date de fin au présent.
- `404` si la campagne n'existe pas.
- `409` si son état l'interdit : pas encore commencée, sans date de début,
  déjà clôturée, ou annulée.

Deux points de comportement :

- **Une date de fin déjà passée est conservée.** La campagne s'est arrêtée ce
  jour-là ; la clôture ne fait que l'acter. Une date de fin future, elle, est
  ramenée au présent — c'est le sens d'une clôture anticipée.
- **Le critère est la date de début, pas le statut stocké.** Voir la limite
  ci-dessous.

## Limite connue : le statut stocké est figé

`CampaignStatus` est calculé une fois, à la création, et ne bouge plus : une
campagne créée avec une date de début future naît `SCHEDULED` et **le reste pour
toujours**, même longtemps après avoir commencé. Rien ne la fait basculer en
`ACTIVE`.

La clôture contourne le problème en se fondant sur les dates : une campagne
`SCHEDULED` dont la date de début est passée est clôturable, et c'est bien le
comportement attendu. Mais l'affichage, lui, reste incohérent — le badge
annoncera « Programmée » pour une campagne en cours.

Le corriger est un sujet à part entière : dériver le statut à la lecture dans
`CampaignMapper`, ou le recalculer périodiquement comme le fait l'ordonnanceur
pour les occurrences. À trancher avant de brancher le bouton « Clôturer », qui
aura besoin de savoir quelles campagnes sont clôturables.

## Vérification

```bash
cd apps/back && mvn -B verify   # 168 tests, BUILD SUCCESS
```

Tests ajoutés :

- `CampaignPersistenceTest` (5) — campagne à durée indéterminée, campagne sans
  aucune date, mise à jour d'une campagne déjà commencée, date de fin au
  présent, et la clôture complète contre une base H2. Les trois derniers
  échouaient avant le retrait des contraintes.
- `CampaignServiceTest.Close` (9) — arrêt au présent, date de fin future ramenée
  au présent, date de fin passée conservée, clôture d'une campagne restée
  `SCHEDULED`, et les refus : pas commencée, sans date de début, déjà clôturée,
  annulée, introuvable.
- `CampaignControllerTest.Close` (3) — `200`, `404`, `409`.

## Reste à faire

- **Le bouton « Clôturer »** côté front, et la méthode de service qui appelle
  l'endpoint.
- **Le statut figé**, décrit plus haut.
- **Annuler une campagne** : `CANCELED` existe dans l'énumération mais rien ne
  le pose, exactement comme `COMPLETED` avant cette PR.
