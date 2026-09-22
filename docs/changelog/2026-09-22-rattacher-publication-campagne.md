# Rattacher une publication à une campagne

**Date** : 2026-09-22
**Périmètre** : `apps/back`, `apps/front`

## Contexte

La page Campagnes montre les publications rattachées à chaque campagne, mais
rien ne permettait d'en rattacher une. Le rattachement se fait depuis la page
Publications, comme dans la maquette : c'est la publication qu'on assigne à une
campagne, pas l'inverse.

Côté front, tout était déjà en place — la carte affiche un badge de campagne, un
bouton « Ajouter à une campagne » / « Changer de campagne », et les filtres
proposent « Sans campagne » et la liste des campagnes. Seul manquait ce à quoi
brancher tout cela.

## Un filtre qui ne pouvait rien trouver

`PublicationDTO` n'exposait que `id`, `content`, `status` et `medias` : jamais la
campagne. Le front, lui, la déclarait — en optionnel — et s'en servait pour le
badge de la carte et pour le filtre « campagne ».

Conséquence, invisible faute de message d'erreur : le badge n'apparaissait
jamais, filtrer par campagne ne renvoyait rien, et « Sans campagne » renvoyait
**tout**. Exposer la campagne répare les trois d'un coup.

Elle est exposée sous une forme réduite, `PublicationCampaignDTO` (identifiant
et nom). Un `CampaignDTO` complet embarquerait ses publications, dont
celle-ci — le cycle que `GET /api/campaigns` avait déjà connu.

## L'endpoint

`PUT /api/publications/{id}/campaign`, avec `{ "campaignId": 3 }` — ou `null`,
ou un corps vide, pour détacher. Un `PUT` sur un sous-chemin nommé plutôt qu'un
`PATCH` de la publication : l'appel **remplace** le rattachement et non
l'inverse, et il est idempotent. C'est la même forme que
`PUT /api/publications/{id}/medias`.

- `200` avec la publication à jour, campagne comprise.
- `404` si la publication ou la campagne est inconnue.

Seule la publication est enregistrée : c'est elle qui porte la clé étrangère.
Passer par `Campaign.addPublication` rendrait la campagne sale et Hibernate la
revaliderait sans raison.

## Côté front

- La modale « Assigner à une campagne » reprend la maquette : rappel de la
  publication concernée, liste déroulante des campagnes précédée d'« Aucune
  campagne », boutons Annuler et Assigner. Le bouton reste inactif tant que la
  sélection n'a pas changé — réassigner la même campagne ne ferait qu'un appel
  pour rien.
- `PublicationsService.assignToCampaign` appelle enfin l'API : c'était l'un des
  deux `TODO(back)` du service, qui relayait une indisponibilité. Il ne reste
  que la génération XLSX.
- `Publication.campaign` n'est plus optionnel côté modèle : l'API le renvoie
  systématiquement.

## Vérification

```bash
cd apps/back && mvn -B verify   # 179 tests, BUILD SUCCESS
cd apps/front && npm test       # 36 fichiers, 317 tests
```

Parcours navigateur (`ng serve`, API bouchonnée faute de base locale) : badge de
campagne enfin visible sur la carte, rattachement à une autre campagne,
détachement, et filtre par campagne — qui ne renvoyait rien auparavant et
renvoie maintenant la bonne publication. Aucune erreur console.

Tests ajoutés :

- `PublicationServiceCampaignTest` (6) — rattachement, détachement par
  identifiant nul, remplacement d'une campagne précédente, enregistrement de la
  seule publication, publication inconnue, campagne inconnue.
- `PublicationControllerCampaignTest` (5) — contrat de l'endpoint, dont le
  détachement par `null` et par corps vide, et les deux `404`.
- Front : la modale (9) et le parcours complet depuis la page Publications (4),
  plus le `PUT` du service.

## Reste à faire

- **Rattacher depuis la page Campagnes** : la maquette ne le propose pas, et ce
  n'est pas nécessaire tant que le geste existe côté Publications.
- **`createdAt`** : toujours pas exposé par `PublicationDTO`, alors que la
  maquette date les cartes. Le champ reste optionnel côté front.
- **Génération XLSX** : le dernier `TODO(back)` du service des publications.
