# Médiathèque — socle back : entité, stockage et API

**Date** : 2026-08-23
**Branche** : `claude/medias-socle-back`

## Contexte

Les publications ne portaient que du texte. Rien n'existait pour attacher une
affiche ou un visuel à un post : ni entité, ni endpoint d'upload, ni stockage —
le `pom.xml` ne contenait aucune dépendance de fichiers.

Deux amorces attendaient pourtant dans le code : la carte publication réserve un
emplacement de vignette (« la médiathèque n'existe pas encore côté domaine »), et
le changelog de la navbar note qu'une entrée **Médiathèque** est prévue par la
maquette Figma.

Ce premier lot pose le socle serveur. Il ne change rien à l'interface : la page
dédiée fait l'objet du lot suivant, le rattachement aux publications et la
diffusion Facebook avec images du lot d'après.

## Décisions

- **Stockage sur volume disque**, pas de MinIO ni de S3 : le VPS est en 1 vCPU /
  3,9 Go, et un service de stockage objet supplémentaire y serait disproportionné.
  L'interface `MediaStorageService` isole ce choix — une implémentation S3 se
  substituerait sans toucher au service métier.
- **Bibliothèque réutilisable** plutôt que pièces jointes : un média existe
  indépendamment des publications. Le rattachement N-N arrive au lot 3.
- **Formats acceptés** : JPEG, PNG, WebP et PDF. Le SVG est délibérément exclu —
  servi en `inline`, il exécuterait son JavaScript dans l'origine de l'application.

## Changements

### Domaine et API — nouveau package `media`

- `Media` : métadonnées seules, le binaire vit sur le disque, désigné par
  `storageKey`. Table `medias`.
- `MediaController` sur `/api/medias` : dépôt multipart, liste (filtrable sur
  `archived`), détail, service du binaire, modification des métadonnées,
  suppression.
- `MediaTypeDetector` : reconnaissance par nombres magiques. Ni le `Content-Type`
  annoncé par le navigateur ni l'extension ne sont crus — tous deux viennent du
  client et se falsifient en renommant le fichier.
- `FileSystemMediaStorage` : répartition en `aaaa/MM/`, nom généré (UUID),
  écriture dans un fichier temporaire puis déplacement atomique, et refus de toute
  clé qui sortirait de la racine.
- Cohérence disque/base : le fichier est supprimé si la transaction du dépôt
  échoue, et n'est effacé qu'**après** le commit d'une suppression.

### Configuration et durcissement

- `spring.servlet.multipart.max-file-size` à 15 Mo — le défaut est de **1 Mo**.
- `client_max_body_size 20m` sur `location ^~ /api/` du nginx du front, dont le
  défaut est aussi de 1 Mo : sans lui, tout dépôt d'affiche était rejeté en 413
  avant même d'atteindre le back.
- `PATCH` ajouté aux méthodes CORS, qui ne le contenait pas.
- Nouveaux codes d'erreur au format `ApiError` : 415 (format refusé), 413 (fichier
  trop gros — il ressortait en 500 nu), 404, 500 (panne de stockage).
- Le binaire est servi avec son type réel, un `ETag` égal au SHA-256, un cache
  immuable d'un an et `X-Content-Type-Options: nosniff`.

### Infrastructure

- Volume `media-data` sur le service `back` en préproduction, monté sur
  `/var/lib/agorapilot/media`.
- Le Dockerfile crée ce répertoire et le donne à l'utilisateur `agorapilot`
  **avant** le `USER` : Docker recopie les permissions du chemin présent dans
  l'image lorsqu'il monte un volume nommé encore vide. Sans cela le volume
  appartient à root et le back démarre sans droit d'écriture.
- Dépendance `com.twelvemonkeys.imageio:imageio-webp` : le JDK ne fournit aucun
  lecteur WebP, les dimensions d'un WebP seraient illisibles. Elle servira aussi au
  transcodage vers JPEG à la diffusion Facebook, dont l'API Photos n'accepte pas le
  WebP.

## Vérification

`mvn -B verify` : **76 tests, tous verts**, dont 40 nouveaux — détection de format,
stockage (`@TempDir`), service et contrat HTTP du contrôleur.

Vérification bout en bout en exécutant réellement l'application (H2 en mémoire,
racine de stockage jetable) :

| Cas | Résultat |
|---|---|
| Dépôt d'un PNG 1200×1800 | 201, dimensions lues, fichier identique octet pour octet, empreinte conforme |
| Dépôt d'un WebP | 201, dimensions lues via TwelveMonkeys |
| Dépôt d'un PDF | 201, dimensions nulles, sans tentative de lecture d'image |
| Script shell renommé en `.jpg` et annoncé `image/jpeg` | 415 |
| Fichier vide | 415 |
| SVG | 415 |
| Fichier de 20 Mo | 413 avec un corps lisible |
| Relecture avec `If-None-Match` | 304 |
| Nom de fichier `../../../etc/pas"swd.png` | segments retirés, rien écrit hors de la racine |
| Suppression | 204, ligne **et** fichier disparus, aucun `.part` résiduel |

Les trois tests à contexte Spring complet reçoivent désormais une racine de
médiathèque jetable : sans cela, ils écrivaient dans l'arbre de travail.

## Reste à faire

- Lot 2 : page Médiathèque côté front (upload, galerie, entrée de navbar).
- Lot 3 : rattachement N-N aux publications et diffusion Facebook via l'API Photos.
- La suppression d'un média rattaché à une publication devra répondre 409 — la
  vérification n'a de sens qu'une fois la table de liaison en place (lot 3).
- Le dédoublonnage par empreinte est possible (le SHA-256 est déjà en base) mais
  n'est pas implémenté : deux dépôts du même fichier donnent deux médias distincts.
