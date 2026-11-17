# Carte publication : refonte de la hiérarchie des actions

**Date** : 2026-11-17
**Périmètre** : `apps/front` (`publication-card`, spec de la page Publications)

## Contexte

La carte d'une publication empilait jusqu'à huit boutons de même poids :
XLSX, Facebook, Programmer, visuels, campagne, Modifier, Supprimer. Le texte
était peu lisible, aucun titre ne ressortait, et deux boutons restaient grisés
sur un brouillon. Cette version reprend la direction « design system actuel »
explorée sur le canevas de design. Les couleurs, rayons et ombres restent ceux
des tokens existants.

## Changements

- **Titre** : la première ligne du texte devient le titre (17 px, gras, deux
  lignes au plus), et le reste devient un extrait de deux lignes. Un texte d'un
  seul bloc est coupé après sa première phrase si elle fait moins de
  120 caractères. Le modèle et l'API ne changent pas.
- **Une action principale qui dépend du statut** :
  - Publication vérifiée : « Diffuser ▾ » ouvre un menu avec « Publier
    maintenant » (Facebook) et « Programmer… ».
  - Brouillon : « Relire et valider » ouvre l'édition, avec la mention « La
    diffusion se débloque une fois la publication vérifiée ».
  - Diffusion ou suppression en cours : un bouton d'attente verrouillé.
- **Les actions vont là où elles s'appliquent** :
  - Le visuel entier est le bouton qui mène aux visuels. Il porte un compteur
    du nombre total de visuels.
  - Sans visuel, une zone en pointillés « Ajouter un visuel » le remplace.
  - L'étiquette de campagne est cliquable pour en changer, ou affiche
    « + Campagne » quand il n'y en a pas.
- **Bouton crayon** pour modifier une publication vérifiée.
- **Menu « ⋯ »** : Gérer les visuels, Changer de campagne, Exporter en XLSX et
  Supprimer.
- **Suppression** : la confirmation remplace le pied de la carte, toujours sans
  `confirm()` natif.
- **Menus** : un seul ouvert à la fois. Ils se ferment avec Échap ou un clic
  hors de la carte. La carte n'a plus d'`overflow: hidden`, pour que les menus
  puissent passer par-dessus les cartes voisines.
- **Droits** : une action qu'un compte ne peut pas faire n'est pas proposée.
  Entrées et sorties du composant inchangées, la page Publications n'a pas
  bougé.
- **Spec de la page** : elle déclenche désormais les sorties de la carte
  (`emitFrom`) au lieu de cliquer sur ses boutons internes. Le détail des menus
  est couvert par le spec de la carte.

## Vérification

- `npm test` (front) : 65 fichiers, 665 tests verts, dont 32 pour la carte.
- Page Publications ouverte dans le navigateur intégré, avec une API bouchonnée
  sur 8080 (pas de back local). Vérifié : carte vérifiée avec trois visuels,
  brouillon sans visuel, menus « Diffuser » et « ⋯ », confirmation de
  suppression, affichage à 375 px de large.

## Commit

Voir la PR `claude/carte-publication` → `develop`.
