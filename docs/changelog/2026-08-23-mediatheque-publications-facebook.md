# Médiathèque — rattachement aux publications et diffusion Facebook

**Date** : 2026-08-23
**Branche** : `claude/medias-publications-facebook`

## Contexte

Troisième et dernier lot de la gestion des médias. Le premier a posé le socle back
(entité, stockage sur volume, API `/api/medias`), le second la page Médiathèque.
Les visuels existaient donc, mais restaient sans lien avec les publications : la
carte publication affichait toujours son placeholder, et `FacebookClient` ne
postait que du texte sur `/{page-id}/feed`.

Ce lot relie les deux et va jusqu'à la diffusion réelle des images.

## Changements

### Liaison publication ↔ médias

`PublicationMedia` : entité d'association explicite plutôt qu'un `@ManyToMany`,
parce que l'ordre compte — le premier visuel sert de vignette et de première photo.
Table `publication_medias`, colonne `position`, unicité `(publication_id, media_id)`.

**Aucune cascade vers `Media`** : détacher un visuel d'une publication ne l'efface
pas de la médiathèque, où d'autres publications peuvent s'en servir. C'est ce qui
distingue une bibliothèque de simples pièces jointes.

`PUT /api/publications/{id}/medias` remplace la liste ordonnée en un appel — plus
simple que des ajouts et retraits unitaires quand on réordonne, et idempotent.
`PublicationDTO` gagne un champ `medias`.

`DELETE /api/medias/{id}` répond désormais **409** quand une publication utilise le
média : l'effacer viderait silencieusement des publications existantes, y compris
déjà diffusées. Archiver reste possible.

### Diffusion Facebook

`FacebookClient` bascule de « toujours `/feed` en JSON » vers trois chemins :

| Visuels | Appel |
|---|---|
| aucun | `POST /{page-id}/feed` en JSON — inchangé |
| un | `POST /{page-id}/photos` en multipart, publication directe |
| plusieurs | un dépôt non publié par photo, puis un post au fil qui les rattache par `media_fbid` |

Le binaire est **envoyé** en multipart, jamais désigné par une URL : les serveurs
de Facebook ne peuvent pas atteindre nos environnements, fermés au réseau WireGuard
par Traefik. Le champ `url` de l'ancien `FacebookPostRequest`, resté inutilisé
depuis l'origine, disparaît avec lui.

`PublishableImageFactory` fait les deux traductions qu'aucun réseau social ne
dispense :

- les **PDF sont écartés** — ils ne sont pas publiables comme photo ;
- les **WebP sont transcodés en JPEG** — l'API Photos ne les accepte pas. La
  transparence est aplatie sur du blanc, faute de canal alpha en JPEG.

Les médias écartés sont remontés et tracés plutôt que tus : une diffusion amputée
en silence est pire qu'un échec franc. Un fichier illisible fait sauter la seule
photo concernée, pas la publication entière. Le read timeout de `FacebookConfig`
passe de 10 à 60 s : suffisant pour un post texte, trop court pour une affiche de
plusieurs mégaoctets.

### Front

- La carte publication affiche la **première image** rattachée comme vignette ; un
  PDF placé en tête est ignoré pour ce rôle. Un compteur `+n` signale les visuels
  suivants. Le commentaire « la médiathèque n'existe pas encore côté domaine »
  disparaît.
- `media-picker-modal` : sélection ordonnée depuis la médiathèque, avec rangs,
  flèches de réordonnancement et avertissement explicite sur les PDF. Les médias
  archivés en sont exclus.
- L'habillage commun des modales (fond, panneau, en-tête, actions) est remonté dans
  `styles.css` : trois composants le dupliquaient, et la troisième copie faisait
  dépasser le budget CSS par composant.

## Un défaut que seuls les tests d'intégration pouvaient voir

Le premier jet vidait la liste des rattachements puis la reconstruisait. Sur un
simple **réordonnancement**, Hibernate émet les INSERT avant les DELETE : les mêmes
couples `(publication_id, media_id)` étaient réinsérés et la contrainte d'unicité
sautait, en 500.

Les tests unitaires du service, qui mockent le repository, ne pouvaient pas le
voir — le premier `PUT` depuis une liste vide passait, et c'est ce qu'ils
couvraient. Le défaut est apparu en manipulant l'interface.

Le service réconcilie désormais la liste en place : les rattachements existants sont
réutilisés et seule leur position change. `PublicationMediasPersistenceTest`
(`@SpringBootTest` + H2) verrouille le cas.

## Vérification

`mvn -B verify` : **117 tests back verts**, dont 16 nouveaux — rattachement,
persistance, préparation des images, les trois chemins de `FacebookClient` et le
publisher.

`npm run lint`, `npm test` (**269 tests front verts**, dont 30 nouveaux) et le build
de production, sans avertissement de budget CSS.

Bout en bout, en exécutant réellement l'application (H2, racine de stockage
jetable) :

| Cas | Résultat |
|---|---|
| `PUT` d'une liste ordonnée | 200, ordre respecté dans le DTO et en base |
| Réordonnancement depuis l'interface | 200 (500 avant correctif) |
| Même liste deux fois | 200, idempotent |
| Suppression d'un média rattaché | 409 |
| Même média deux fois dans la liste | 400 |
| Média inconnu | 404, et la sélection existante reste intacte |
| Carte publication avec un PDF en tête | vignette prise sur l'image suivante, compteur `+1` |

La diffusion Facebook avec images **reste à valider en préproduction** : le token de
page vit en base de préprod, elle n'est pas rejouable en local. Les appels sont
couverts par `FacebookClientTest` (`MockRestServiceServer`), qui vérifie notamment
qu'aucune URL n'est envoyée à la place du binaire.

## Reste à faire

- Valider une diffusion à une puis trois photos sur la page de préproduction.
- Le nombre de photos est plafonné à 10 par l'API Graph ; au-delà, les visuels sont
  écartés et tracés, mais rien n'en avertit l'utilisateur avant l'envoi.
