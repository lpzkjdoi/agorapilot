# Modifier et supprimer une publication

## Contexte

Une publication pouvait être créée, listée, programmée et rattachée à une
campagne ou à des visuels, mais ni modifiée ni supprimée. Une faute de frappe
dans un post déjà programmé ne se corrigeait pas.

## Changements

### Back

- `PUT /api/publications/{id}` (`UpdatePublicationRequest` : `content`, `status`
  obligatoires) remplace le texte et le statut. Les diffusions encore
  programmées partiront avec le nouveau texte, puisque le publisher lit la
  publication au moment de diffuser. Celles déjà publiées ne changent pas sur
  Facebook.
- `DELETE /api/publications/{id}` répond `204` :
  - les diffusions programmées sont annulées, et leurs jours sont répartis à
    nouveau dans la fenêtre de publication ;
  - les échecs qui n'ont rien publié nulle part disparaissent avec la
    publication ;
  - une publication **déjà diffusée** n'est pas effacée mais **archivée**
    (`archived` existait déjà sur l'entité) : ses diffusions publiées restent
    dans le calendrier comme historique ;
  - tout ou rien : si une diffusion est en cours ou imminente (moins de 5 min),
    rien n'est touché et la réponse est `409` avec le motif.
- Une publication archivée est traitée comme absente partout : liste,
  modification, visuels, campagne, programmation, diffusion immédiate (`404`),
  publications d'une campagne. La reprise d'un échec gardé en historique est
  refusée (`409`).
- `PublicationOccurrenceService.releaseOccurrencesOf` porte la logique côté
  occurrences. `isModifiable` est extrait de `findModifiable` pour la partager.

### Front

- `PublicationsService.updatePublication` / `deletePublication`.
- La modale de création sert aussi à la modification : `publication` en entrée,
  formulaire pré-rempli, bouton « Enregistrer », rappel que les diffusions déjà
  publiées ne changent pas.
- Carte publication : boutons « Modifier » et « Supprimer ». La suppression se
  fait en deux temps, comme dans la médiathèque : « Supprimer, et annuler ses
  diffusions programmées ? ». Le bouton est verrouillé pendant l'appel.
- Page Publications : la carte est mise à jour ou retirée, et le motif d'un
  `409` est relayé en notification.
- Budget `anyComponentStyle` (avertissement) relevé de 2,5 kB à 3 kB : la
  feuille de la carte était déjà au ras du seuil (pas d'avertissement avant ce
  changement), et les styles de suppression le dépassaient de quelques dizaines
  d'octets. Le seuil d'erreur reste à 4 kB.

## Vérification

- `mvn -B verify` : 302 tests, dont `PublicationCrudPersistenceTest` (11, contre
  H2 avec une horloge figée : modification, effacement, annulation et nouvelle
  répartition du jour, archivage avec historique, publication archivée
  invisible, refus pendant une diffusion imminente ou en cours) et
  `PublicationControllerCrudTest` (7). Les tests unitaires existants stubbent
  désormais `findByIdAndArchivedFalse`.
- `npm test` : 462 tests (service, formulaire, modale, carte, page) ; `ng lint`
  et `ng build` sans avertissement.
- Vu dans le navigateur contre une API bouchonnée : modification pré-remplie et
  enregistrée, confirmation de suppression, refus `409` relayé, suppression
  réussie qui retire la carte et met à jour les compteurs.

## Reste à faire

- Restaurer une publication archivée (aucune interface pour l'instant).
