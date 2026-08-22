# Correctif : `GET /api/occurrences/weekly` renvoyait un JSON tronqué

- **Date** : 2026-08-22
- **Branche** : `claude/fix-recursion-occurrences-weekly` (depuis `develop`)
- **Périmètre** : `apps/back`

## Contexte

Sur la préproduction, le calendrier hebdomadaire du dashboard ne se chargeait
plus. La console du navigateur affichait :

```
Http failure during parsing for /api/occurrences/weekly
status: 200, ok: false
error: { error: SyntaxError, text: '{"2026-08-22T00:00":[{"id":1,…"occurrence":{…"deliveries":[{…"occurrence":{…' }
```

Un 200 dont le corps ne se parse pas : la réponse était **tronquée**.

### Cause

`PublicationOccurrenceDTO` déclarait ses livraisons comme
`List<PublicationDelivery>` — l'**entité**, pas le DTO. Or
`PublicationDelivery` référence en retour son `PublicationOccurrence`. Jackson
sérialisait donc le cycle `occurrence → deliveries → occurrence → …` sans jamais
s'arrêter.

Reproduit en test jetable sur le graphe d'entités :

```
com.fasterxml.jackson.databind.JsonMappingException:
Document nesting depth (1001) exceeds the maximum allowed (1000)
```

Le mécanisme du 200 illisible tient à ce que l'exception survient **après** que
la réponse a commencé à être écrite : le statut est déjà committé, le client
reçoit un JSON coupé au millième niveau d'imbrication.

Ce n'est pas une régression d'un changement récent : le défaut existe depuis
l'écriture de l'endpoint (`5bfe891`). Il ne se déclenche que si une occurrence de
la semaine en cours porte au moins une livraison — ce qui n'était jamais arrivé
en préproduction avant la première publication Facebook réelle, créée le
2026-08-22 à 18:20. Aucun test back ne couvrait cet endpoint.

## Changements

- **`PublicationOccurrenceDTO`** — `deliveries` passe à
  `List<PublicationDeliveryDTO>`. Le DTO existait déjà (`id`, `occurrenceId`,
  `channel`, `status`, `publishedAt`, `externalId`) et n'était utilisé que par
  `POST /api/publications/{id}/deliveries` ; il porte l'occurrence par son
  identifiant, donc sans cycle. Un commentaire de classe explique pourquoi
  l'entité ne doit pas y revenir.
- **`PublicationOccurrenceMapper`** — injecte `PublicationDeliveryMapper` et
  mappe la collection.

Le contrat vu du front est inchangé côté champs consommés (`id`, `channel`,
`status`) : `apps/front/src/app/features/occurrences/occurrence.model.ts` n'a pas
besoin d'évoluer.

## Vérification

- `cd apps/back && mvn -B verify` → **36 tests verts**, dont 4 ajoutés :
  - `PublicationOccurrenceMapperTest` (3) — les livraisons sont bien des DTO ;
    la sérialisation Jackson aboutit et ne contient aucune clé `occurrence` ;
    une occurrence sans livraison donne une liste vide.
  - `PublicationOccurrenceControllerTest` (1) — contrat HTTP de l'endpoint :
    objet indexé par jour, livraisons à plat, et `deliveries[0].occurrence`
    absent — le garde-fou de cette régression.
- Boucle reproduite avant correctif sur le graphe d'entités, puis vérifiée
  disparue après.

## Suite

`Occurrence.status` est déclaré côté front mais absent de
`PublicationOccurrenceDTO` ; le front ne l'utilise nulle part. À trancher
séparément : exposer le champ, ou le retirer du modèle TypeScript.

## Commit

_voir PR_
