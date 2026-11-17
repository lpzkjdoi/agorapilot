# Diffusions retenues par une suspension : « En attente », déplaçables et annulables

## Contexte

Quand le canal Facebook est suspendu (compte bloqué, jeton invalidé),
l'ordonnanceur laisse les livraisons dues en attente, sans appel à Facebook.
Une fois leur heure passée, ces diffusions restaient affichées « Programmée » :
- elles ne pouvaient être ni déplacées ni annulées, car le back les jugeait
  imminentes (horizon figé de 5 minutes) ;
- la suppression d'une publication ou la clôture d'une campagne qui en portait
  une était refusée pour la même raison ;
- elles finissaient en échec au bout du retard maximal (1 h par défaut).

C'était une limite connue de la
[suspension du canal](./2026-10-16-suspension-canal-facebook.md#limites-connues),
reprise dans le « fortement recommandé » du jalon v1.0.

## Changements

### Back

- **`OccurrenceHold`** : une diffusion est *retenue* quand elle est
  `SCHEDULED`, que son heure est passée, qu'aucun canal n'a encore été servi et
  qu'au moins un de ses canaux est suspendu. Ce n'est pas un statut stocké :
  l'état se déduit de la suspension et disparaît avec elle. Aucune migration.
- **`PublicationOccurrenceDTO.held`** : nouveau booléen exposé au front.
- **`isModifiable`** accepte une diffusion retenue. Rien ne part tant que le
  canal reste suspendu, donc la toucher n'est pas une course avec
  l'ordonnanceur. Cela débloque d'un coup :
  - le déplacement (`PUT /api/occurrences/{id}/schedule`) et l'annulation
    (`DELETE /api/occurrences/{id}`) ;
  - la suppression d'une publication et l'annulation ou la clôture d'une
    campagne qui portent une diffusion retenue.
- Le comportement de l'ordonnanceur ne change pas. La diffusion part à la levée
  de la suspension si elle est encore dans le retard maximal, et passe en échec
  au-delà.

### Front

- `Occurrence.held` dans le modèle. `isModifiable` suit la même règle que le
  back : « Modifier » et le glisser-déposer sont proposés pour une diffusion
  retenue.
- **Bulle du jour** : badge ambre « En attente » à la place de « Programmée »,
  avec une explication : la diffusion partira à la reprise des diffusions, ou
  passera en échec au-delà d'une heure de retard. La mention « vous pouvez
  encore la déplacer ou l'annuler » n'apparaît qu'avec le droit de programmer.
- **Pastille du calendrier** : teinte ambre, icône ⏸ et infobulle « Diffusion en
  attente : canal suspendu ».

## Limite connue

Si quelqu'un lève la suspension à l'instant même où une autre personne annule
une diffusion retenue, l'ordonnanceur peut la prendre en charge avant que
l'annulation soit validée. Les entités n'ont pas de verrou optimiste. La
fenêtre est de l'ordre de la milliseconde, et le cas reste théorique pour une
équipe de cette taille.

## Vérification

- `mvn -B verify` : 434 tests verts, dont :
  - `OccurrenceHoldTest` (4 cas : retenue, à venir, canal non suspendu, déjà
    prise en charge ou tranchée) ;
  - 5 cas ajoutés à `OccurrenceSlotsPersistenceTest`, contre H2 : indicateur
    exposé, annulation, déplacement vers un autre jour, refus sans suspension,
    pas de retenue avant l'heure.
- `npm test` : 669 tests verts. Nouveaux cas dans `occurrence.utils.spec.ts`,
  la pastille et la bulle du jour (avec et sans droit de programmer).
- `ng lint` et `ng build` OK.
- Rendu non vérifié dans le navigateur.

## Commit

_voir PR_
