# Grille des publications : jusqu'à 5 cartes par ligne

## Contexte

Avec des colonnes de 22rem au minimum, la page Publications n'affichait que
3 cartes par ligne, même sur un grand écran. Maxime voulait en voir jusqu'à 5.

## Changements

- `.publications-grid` : la largeur minimale d'une colonne est désormais la plus
  grande de deux valeurs :
  - **17rem**, de quoi loger « Diffuser » et deux boutons-icônes sans débordement ;
  - **un cinquième de la grille**, ce qui plafonne le nombre de colonnes à 5,
    même sur un écran très large.
- Sur un petit écran, la grille repasse à 4, 3, 2 puis 1 colonne. Le
  `min(100%, …)` évite tout débordement horizontal sur mobile.
- Roadmap mise à jour :
  - le bouton Supprimer de la carte (PR #80) et la grille sont cochés ;
  - les sauvegardes sont mises de côté à la demande de Maxime, à reprendre avant
    la mise en production.

## Vérification

Rendu vérifié dans le navigateur intégré avec une fausse API (8 publications,
brouillons et vérifiées) :

| Largeur | Colonnes | Largeur d'une carte |
|---|---|---|
| 1920 px | 5 | 358 px |
| 1512 px | 5 | 277 px |
| 1280 px | 4 | 294 px |
| 375 px (mobile) | 1 | 343 px |

Aucun bouton du pied de carte ne déborde (« Diffuser », « Relire et valider »),
et la page ne défile pas horizontalement sur mobile.

`npm test`, `ng lint` et `ng build` passent. Le changement ne touche que du CSS,
sans comportement nouveau à couvrir par un test unitaire.

## Commit

_voir PR_
