# Diffusion immédiate d'une publication sur Facebook

## Contexte

Le bouton « Publier sur Facebook » de la carte publication existait depuis
l'intégration de la page Publications, mais n'avait aucun endpoint derrière lui :
`PublicationsService.publishOnFacebook()` levait volontairement une erreur et la
page se contentait d'un toast « Cette action n'est pas encore disponible. ».

Côté back, tout était pourtant en place sauf le déclencheur : `FacebookPublisher`
sait diffuser une occurrence et tracer le résultat sur la livraison, et
`FacebookTokenService` entretient le token de page. Il manquait un chemin
permettant de publier à la demande, sans attendre une occurrence planifiée.

Cette brique est aussi le socle du déclenchement automatique à l'heure prévue
(cf. roadmap du README) : le scheduler réutilisera le `PublisherRegistry` et la
règle de passage d'une occurrence au statut `PUBLISHED`.

## Choix de modélisation

Le domaine ne connaît que des livraisons rattachées à une occurrence
(`publication_deliveries.occurrence_id` est `NOT NULL`). Publier « maintenant »
crée donc **une occurrence datée de l'instant présent**, porteuse d'une unique
livraison sur le canal demandé. Conséquences voulues :

- la diffusion immédiate est tracée comme n'importe quelle diffusion planifiée
  (statut, `publishedAt`, `externalId` du post Facebook, message d'erreur) ;
- elle apparaît dans le calendrier de la semaine du dashboard, qui interroge
  `GET /api/occurrences/weekly` ;
- aucun chemin parallèle à maintenir le jour où le scheduler arrivera.

## Changements

### Back — `apps/back`

| Fichier | Rôle |
|---|---|
| `publisher/PublisherRegistry.java` | résout le `Publisher` d'un canal ; ajouter une implémentation (Intramuros) suffira à la rendre disponible |
| `publication/PublicationDeliveryService.java` | crée l'occurrence immédiate, déclenche la diffusion, remonte le résultat |
| `publication/dtos/PublicationDeliveryDTO.java` + `dtos/mappers/PublicationDeliveryMapper.java` | la livraison exposée au front |
| `publication/requests/CreatePublicationDeliveryRequest.java` | corps de la requête, `channel` obligatoire |
| `publication/exceptions/DeliveryFailedException.java` | le canal distant a refusé |
| `publication/exceptions/UnsupportedDeliveryChannelException.java` | aucun publisher pour ce canal |

Nouvel endpoint :

```
POST /api/publications/{id}/deliveries
{ "channel": "FACEBOOK" }
```

| Code | Cas |
|---|---|
| `201` | livraison effectuée ; le corps porte `status`, `publishedAt`, `externalId`, `occurrenceId` |
| `400` | `channel` absent ou inconnu |
| `404` | publication inexistante |
| `501` | canal sans publisher branché (Intramuros aujourd'hui) |
| `502` | Facebook a refusé ; le corps porte le motif renvoyé par le canal |

Deux points d'attention dans le service :

- `@Transactional(noRollbackFor = DeliveryFailedException.class)` : le publisher
  enregistre lui-même l'échec sur la livraison (`FAILED` + message). Un rollback
  effacerait cette trace en même temps qu'il remonterait l'exception, et
  l'échec deviendrait invisible en base.
- une occurrence ne passe à `PUBLISHED` que lorsque **toutes** ses livraisons le
  sont, règle directement réutilisable par le scheduler à venir.

### Correctif — `config/ApiError`

`ApiError` ne portait que `@Setter` : Jackson ne sérialisait donc aucun champ et
**toutes** les erreurs de l'API partaient en `{}`, message compris. Ajout de
`@Getter`. Le contrat d'erreur du nouvel endpoint en dépend — c'est le motif
renvoyé par Facebook qui est affiché à l'utilisateur — mais le correctif profite
aussi aux 404 existants.

### Front — `apps/front`

- `publication.model.ts` : types `DeliveryChannel`, `DeliveryStatus`,
  `PublicationDelivery`, `CreatePublicationDeliveryRequest`.
- `publications.service.ts` : `publishOnFacebook()` appelle réellement
  `POST /api/publications/{id}/deliveries`. `generateXlsx()` et
  `assignToCampaign()` restent en attente de leurs endpoints.
- `publications-page.component.ts` : suit les diffusions en cours par id, toast
  de succès, et toast d'erreur reprenant le motif du `502` quand il est présent
  (`La publication n'a pas pu être diffusée sur Facebook : <motif>`). Un second
  clic pendant une diffusion en cours n'émet pas de requête.
- `publication-card.component` : nouvel input `publishing` — bouton verrouillé,
  loader `sm` et libellé « Publication… » le temps de l'appel.
- `angular.json` : seuil d'alerte `anyComponentStyle` porté de 2 kB à 2,5 kB. Le
  CSS de la carte était à 6 octets de la limite avant ce changement ; le seuil
  d'erreur reste à 4 kB.

## Vérification

```bash
cd apps/back && mvn -B verify     # 32 tests, BUILD SUCCESS
cd apps/front && npm run lint     # All files pass linting
cd apps/front && npm test         # 23 fichiers, 160 tests
cd apps/front && npm run build -- --configuration production
```

Tests ajoutés :

- `PublicationDeliveryServiceTest` (7) — occurrence immédiate portant le bon
  canal, livraison publiée renvoyée, passage de l'occurrence à `PUBLISHED`
  seulement quand tous les canaux sont servis, échec remonté sans perdre la
  trace, publication inconnue, canal sans publisher.
- `PublicationControllerDeliveryTest` (5) — les codes `201`, `404`, `502` (avec
  le motif dans le corps), `501` et `400`.
- `PublisherRegistryTest` (2).
- Front : diffusion et confirmation, motif du `502` relayé dans le toast, absence
  de seconde requête pendant une diffusion en cours, verrouillage et loader du
  bouton de la carte, contrat HTTP du service.

La diffusion réelle vers la page Facebook n'a pas été exercée : elle demande un
token de page valide en base (`POST /admin/facebook/bootstrap`). Le chemin
d'échec côté API est en revanche couvert de bout en bout.

## Reste à faire

- Confirmation avant diffusion : un clic publie immédiatement sur la page
  publique, sans étape intermédiaire. La maquette n'en prévoit pas ; à trancher.
- Scheduler des occurrences dues, qui réutilisera `PublisherRegistry` et la règle
  de complétude de l'occurrence.
- Canal Intramuros : l'endpoint répond `501` tant qu'aucun publisher n'est branché.
