# Campagnes : le calendrier de la campagne

**Date** : 2026-11-10
**Périmètre** : `apps/front`
**Dépend de** : [planification](./2026-11-10-campagnes-planification.md) (lot 3), elle-même sur les lots 1 et 2

## Contexte

Dernier lot de la refonte des campagnes. Le détail listait les diffusions
publication par publication, mais rien ne montrait la campagne dans le temps :
le rythme des diffusions, les trous, et celles qui tombent en dehors de la
période de la campagne.

## Changements

### Onglets dans le détail

Sous les compteurs, deux onglets : **Publications (n)**, la liste du lot 3, et
**Calendrier**. L'onglet choisi reste le même quand on passe à une autre
campagne.

### `CampaignCalendarComponent`

- Il reprend la grille mensuelle et la bulle du jour de la page Calendrier,
  limitées aux diffusions de la campagne. Elles viennent de
  `GET /api/campaigns/{id}/occurrences`, déjà chargé pour le lot 3 : aucun
  appel supplémentaire.
- **Mois d'ouverture** : le mois courant pendant la campagne, celui du début
  pour une campagne à venir, celui de la fin pour une campagne passée.
- **Navigation** : mois précédent et suivant, « Début » (si la campagne a une
  date de début) et « Aujourd'hui ».
- **Période** : les jours du mois qui sortent de la période de la campagne sont
  hachurés.
- **Diffusions hors période** : un encadré les liste avec leur date et leur
  publication. C'est le plus souvent un oubli à corriger.
- **Lecture seule** : pas de glisser-déposer, et la bulle ne propose aucune
  action. Un lien renvoie au Calendrier général pour déplacer ou reprendre une
  diffusion. Gérer ces gestes au même endroit évite d'en dupliquer les règles.

### Grille mensuelle

`CalendarMonthGridComponent` accepte une entrée facultative `period`
(`{ start, end }`, bornes incluses). Les jours du mois qui en sortent reçoivent
la classe `month-cell-off-period`. La page Calendrier ne la renseigne pas et
s'affiche comme avant.

### Styles

Les boutons `.campaign-action` et les onglets passent dans `styles.css`, à côté
des badges de campagne. La feuille du détail dépassait le seuil d'avertissement
du budget CSS par composant (3 ko) depuis le lot 3, et frôlait le seuil
d'erreur (4 ko) avec les onglets.

## Vérification

```bash
cd apps/front && npm test       # 65 fichiers, 660 tests
npx ng build                    # OK, sans avertissement de budget
```

Parcours dans le navigateur (`ng serve`, avec une API simulée, fenêtre de
1250 × 1000) :

- l'onglet Calendrier s'ouvre sur le mois en cours ;
- les jours antérieurs au début de la campagne sont hachurés ;
- la diffusion programmée en janvier, après la fin de la campagne, figure dans
  l'encadré « hors période » ;
- la bulle du 13 novembre s'ouvre en lecture seule.

Tests ajoutés :

- `CampaignCalendarComponent` (9) et `initialMonth` (3) ;
- `CalendarMonthGridComponent`, période (3) ;
- `CampaignDetailComponent`, onglets (3).

## Fin de la refonte

Les quatre lots couvrent ce qui manquait à la page Campagnes : modifier,
clôturer, annuler, supprimer, suivre et piloter la planification, voir le
calendrier. Points laissés ouverts :

- **Annulation et diffusions** : faut-il déprogrammer les diffusions à venir
  d'une campagne annulée ?
- **Lien vers une publication** : la page Publications n'a pas d'adresse propre
  à chaque publication.
