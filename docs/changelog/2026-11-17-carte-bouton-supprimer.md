# Carte publication : un bouton « Supprimer » à la place du menu « … »

## Contexte

Le menu « … » de la carte ne contenait plus que des doublons : visuels et
campagne ont chacun leur accès direct sur la carte, la vignette pour les premiers
et l'étiquette pour la seconde. Il restait l'export XLSX et la suppression. Maxime
a demandé de remplacer ce menu par un bouton « Supprimer » et de ranger l'export
XLSX sous « Diffuser ».

## Changements

- **Pied de carte** : le bouton « … » devient un bouton-icône « Supprimer »
  (corbeille), affiché seulement avec le droit de suppression, rouge au survol et
  verrouillé pendant une suppression. La confirmation en deux temps sur la carte
  ne change pas.
- **Menu « Diffuser »** : il accueille l'entrée « Exporter en XLSX », sous
  « Publier maintenant » et « Programmer… ». Comme le reste du menu, l'export
  n'est donc proposé que pour une publication vérifiée.
- Les entrées « Gérer les visuels » et « Ajouter à une campagne » du menu
  disparaissent : la vignette et l'étiquette de campagne de la carte y mènent
  déjà.
- **Styles partagés** (`styles.css`) : nouvelle variante `.icon-button-danger`
  et état `:disabled` de `.icon-button`. Le CSS de la carte reste ainsi sous son
  niveau précédent (3,73 kB contre 3,75 kB ; le budget de 3 kB était déjà
  dépassé sur `develop`).

L'export XLSX n'a toujours pas d'endpoint côté back : l'entrée affiche
l'avertissement existant (élément de la roadmap).

## Vérification

- `npm test` : 65 fichiers, 666 tests verts. Spec de la carte mis à jour : export
  depuis « Diffuser », absence du menu « … », bouton Supprimer direct (brouillon
  et vérifiée), verrouillé pendant la suppression, masqué sans le droit.
- `ng lint` sans erreur ; `ng build` réussi.
- Rendu non vérifié dans le navigateur.

## Commit

_voir PR_
