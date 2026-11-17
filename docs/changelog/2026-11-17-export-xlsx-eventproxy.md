# Export XLSX d'une publication au format EventProxy

## Contexte

L'entrée « Exporter en XLSX » du menu « Diffuser » de la carte n'avait pas
d'endpoint côté back : elle affichait « Cette action n'est pas encore
disponible ». Le fichier sert à **importer la publication dans un autre outil**.
Maxime a fourni le modèle d'import `EventProxy-2026-11-17.xlsx` :
- une feuille « Sheet » avec 20 colonnes d'en-tête : Titre, Description,
  Publié par, Ville, date et heure de début et de fin, Catégorie, URL image,
  3 liens, Horaires, Tarifs, Adresse postale, Latitude, Longitude, Identifiant
  adresse, Identifiant image ;
- une feuille cachée « Categories » avec 76 catégories, proposées en liste
  déroulante dans la colonne Catégorie.

## Décisions (Maxime, 2026-11-17)

- **Titre** et **Description** : tirés du texte, selon la règle de la carte.
  La première ligne fait le titre, le reste la description, avec ses sauts de
  ligne. Un texte d'un seul bloc est coupé après sa première phrase si elle est
  assez courte.
- **Ville** : « La Buisse », réglable par `agorapilot.export.city`
  (variable `EXPORT_CITY`).
- **Toutes les autres colonnes restent vides**, y compris « Publié par », les
  dates de l'événement et la catégorie : elles se complètent dans l'outil après
  l'import. Les visuels ne sont pas accessibles publiquement, donc aucune URL
  d'image n'est exploitable.

## Changements

### Back

- `GET /api/publications/{id}/xlsx` (`EventProxyExportController`), droit
  `publications:read`. La réponse est un fichier en pièce jointe, nommé
  `EventProxy-AAAA-MM-JJ-publication-<id>.xlsx`. C'est un `GET` et non le
  `POST` prévu par l'ancien TODO du front : l'export ne modifie rien.
- `EventProxyExportService` **reprend le modèle tel quel**, embarqué dans
  `resources/export/eventproxy-modele.xlsx`, et n'ajoute que la ligne 2 à la
  feuille principale :
  - en-têtes, feuille cachée et liste déroulante sont donc exactement ceux
    qu'attend l'import ;
  - aucune bibliothèque tableur n'est ajoutée : les cellules sont écrites en
    chaînes « inline », comme les en-têtes du modèle ;
  - le texte est échappé pour XML et débarrassé des caractères de contrôle ;
  - si le modèle change de forme, l'export échoue franchement au lieu de
    produire un fichier sans la ligne.
- Seule une publication **vérifiée** s'exporte (`409` sinon), comme pour toute
  diffusion. Une publication supprimée répond `404`.

### Front

- `PublicationsService.exportXlsx` télécharge le fichier (`blob`) et lit son
  nom dans `Content-Disposition`.
- `core/files/save-file.ts` propose le fichier au navigateur via un lien
  temporaire.
- La page Publications télécharge le fichier au clic. Une erreur `409` affiche
  « Seule une publication vérifiée peut être exportée ».
- L'action provisoire « pas encore disponible » et son TODO sont retirés.

## Vérification

- `mvn -B verify` vert :
  - `EventProxyExportServiceTest` relit le classeur produit : cellules A2, B2
    et D2, 20 en-têtes, plage `A1:T2`, liste déroulante, 76 catégories, feuille
    cachée, échappement, refus d'un brouillon ou d'une publication inconnue,
    découpage titre et description ;
  - `EventProxyExportControllerTest` vérifie le type, la pièce jointe et le 409 ;
  - `EndpointPermissionsTest` couvre le nouvel endpoint.
- Un fichier réel a été généré :
  - archive intacte, tous les XML bien formés ;
  - aperçu Quick Look de macOS correct (en-têtes et ligne de la publication).
- `npm test` : 675 tests verts. `ng lint` et `ng build` OK.
- Non vérifié : l'import réel dans EventProxy.

## Commit

_voir PR_
