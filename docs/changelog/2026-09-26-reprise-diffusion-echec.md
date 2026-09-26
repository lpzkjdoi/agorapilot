# Reprendre une diffusion en échec, motif lisible des refus Facebook

## Contexte

Le 26/09, cinq publications ont été programmées pour le même jour, réparties à
15:40, 17:05, 18:30, 19:55 et 21:20. La première est partie. Les deux suivantes
ont échoué, avec ce motif affiché dans le calendrier :

```
400 Bad Request: "{"error":{"message":"Confirmez votre identité avant de pouvoir publier au nom de
cette Page. Ouvrez l’application Facebook sur votre téléphone et suivez les
instructions.","type":"OAuthException","code":368,"error_data":{"sentry_block_data":"AetGWG5r…
```

Ce n'est pas un défaut de l'ordonnanceur. L'erreur 368, sous-code 4854002, est un
contrôle de Meta sur le **compte de l'administrateur** de la Page : tant que
l'identité n'est pas confirmée dans l'application Facebook, toute publication au
nom de la Page est refusée. Rien dans le code ne peut ni ne doit le contourner.

L'incident a montré deux manques :

1. **Le motif était illisible.** On stockait le corps brut de la réponse, un JSON
   de près de 1 000 caractères où la consigne utile est noyée.
2. **Une diffusion en échec n'était pas récupérable.** Une livraison `FAILED`
   n'est jamais rejouée automatiquement, c'est voulu : le principe du projet est
   de ne jamais publier deux fois. Mais la reprise explicite, prévue dans le
   « Reste à faire » de la
   [fiabilisation de l'ordonnanceur](./2026-09-25-fiabiliser-ordonnanceur.md),
   n'existait pas. Il fallait reprogrammer la publication depuis sa carte, et
   l'échec restait dans le calendrier.

## Changements

### Motif lisible (back)

- Nouveau `GraphApiError` : lit `message`, `code`, `error_subcode` et
  `error_user_msg` dans le corps d'erreur de l'API Graph. `describe()` produit le
  motif affiché, avec le message destiné à l'utilisateur s'il y en a un, sinon le
  message technique :
  `Confirmez votre identité avant de pouvoir publier au nom de cette Page. … (Facebook, code 368, sous-code 4854002)`.
- `FacebookPublisher` intercepte `RestClientResponseException` et enregistre ce
  motif sur la livraison. Le corps complet, avec le `fbtrace_id` utile au support
  de Meta, part dans les logs (`Occurrence N : Facebook a répondu 400 — {…}`). Un
  corps qui n'a pas la forme Graph, comme une page HTML de proxy, garde l'ancien
  message brut. Les autres échecs sont désormais journalisés aussi.
- `FacebookAdminExceptionHandler` réutilise `GraphApiError` au lieu de sa propre
  lecture du JSON. Le contrat HTTP de `/admin/facebook` est inchangé.

Le motif sert aussi à « Publier maintenant ». La notification devient
« La publication n'a pas pu être diffusée sur Facebook : Confirmez votre
identité… (Facebook, code 368, sous-code 4854002) ».

### Reprise d'une diffusion en échec (back)

`POST /api/occurrences/{id}/retry`, avec le même corps que le déplacement :

```json
{ "date": "2026-09-27" }                  // heure automatique
{ "date": "2026-09-27", "time": "19:00" } // heure fixée (épinglée)
```

- Les livraisons `FAILED` repassent `PENDING`, et leur motif est effacé (il reste
  dans les logs, `Reprise de la livraison …`). Une livraison `PUBLISHED` n'est
  **jamais** remise en file. L'occurrence redevient `SCHEDULED`.
- Placement identique à `PUT /{id}/schedule` : la méthode commune `place()` est
  extraite de `reschedule()`. Sans heure, la diffusion rejoint la répartition du
  jour et prend le dernier créneau.
- Le jour de l'échec n'est **pas** réparti à nouveau. La diffusion n'y occupait
  plus de créneau, et répartir de nouveau aujourd'hui décalerait les diffusions
  restantes de l'après-midi sans raison.
- Réponses : `200` avec l'occurrence, `404` si elle est inconnue, `409` si elle
  n'est pas en échec (une seconde reprise, par exemple), `501` si un canal à
  reprendre n'a plus de publisher, `400` pour une heure passée ou une fenêtre
  close. Tout est transactionnel : une reprise refusée laisse l'échec intact.

### Bouton « Reprendre » (front)

- La bulle du jour propose **« Reprendre »** sous le motif d'une diffusion en
  échec, là où une diffusion programmée propose « Modifier ».
- Nouvelle modale `RetryOccurrenceModalComponent` : elle rappelle la date de
  l'échec et son motif, puis propose un jour (aujourd'hui par défaut) et une
  heure facultative. Une mise en garde demande de vérifier la page Facebook
  d'abord : certains échecs (coupure réseau, diffusion interrompue) surviennent
  après la mise en ligne.
- Page Calendrier : la notification annonce l'heure retenue (« Diffusion
  reprise : elle partira le samedi 26 septembre à 21:40 »). Un refus `400`
  (fenêtre close, heure passée) **laisse la modale ouverte** : il suffit de
  changer de jour. Les autres refus ferment la modale et rechargent le mois.
- `OccurrencesService.retry()`.
- La route `/calendrier` est désormais **chargée à la demande**
  (`loadComponent`). Sans cela, la nouvelle modale portait le bundle initial à
  502 kB, au-delà du budget d'avertissement de 500 kB. Il repasse à 471 kB, et
  la page Calendrier devient un chunk de 34 kB.

Hors maquette : la maquette Figma ne prévoit aucun état d'échec. La modale
reprend la structure de « Modifier la diffusion ».

### Documentation

- Runbook : format du motif, commande pour retrouver la réponse complète dans les
  logs, tableau des refus rencontrés (368/4854002, 190) et reprise.
- README : reprise cochée. Nouvelle ligne de roadmap : suspendre les diffusions
  Facebook quand le compte est bloqué, au lieu de laisser chaque diffusion
  suivante échouer à son tour.

## Vérification

```bash
cd apps/back && mvn -B verify   # 255 tests, BUILD SUCCESS
cd apps/front && npm test       # 45 fichiers, 422 tests (Node 24)
cd apps/front && npm run lint   # All files pass linting
cd apps/front && npx ng build   # sans avertissement de budget, 470,92 kB initial
```

Tests ajoutés :

- `GraphApiErrorTest` (**nouveau**, 5) : lecture du corps réel du 26/09,
  description avec et sans sous-code, préférence pour `error_user_msg`, erreur
  sans message, corps qui ne sont pas une erreur Graph (HTML, succès, `error`
  non objet, vide).
- `FacebookPublisherTest` : motif réduit au message pour un refus 368, corps
  brut conservé pour une 502 HTML.
- `OccurrenceSlotsPersistenceTest` (H2) : reprise vers un jour déjà occupé
  (dernier créneau, livraison `PENDING`, motif effacé), reprise à heure fixe,
  jour de l'échec laissé intact, refus d'une diffusion qui n'est pas en échec,
  reprise refusée qui laisse l'échec intact (rollback).
- `PublicationOccurrenceServiceTest$Retry` : un canal déjà publié n'est jamais
  remis en file, canal sans publisher refusé, occurrence non échouée refusée.
- `PublicationOccurrenceControllerTest$Retry` : 200, 400 sans jour, 404, 409,
  400 avec motif.
- Front : `RetryOccurrenceModalComponent` (**nouveau**, 8), bulle du jour
  (« Reprendre » à la place de « Modifier », émission), page Calendrier (modale
  ouverte depuis la bulle, reprise aujourd'hui par défaut puis rechargement,
  modale gardée sur un `400`, fermée sur un `409`), service (`POST …/retry`).

Vérifié aussi dans le navigateur, contre une API bouchonnée qui reproduit la
journée du 26/09 : motif lisible dans la bulle, modale, reprise qui repasse la
pastille en « programmée », refus d'une heure passée avec la modale gardée
ouverte et la notification d'erreur.

## Reste à faire

- Suspendre le canal Facebook après un blocage du compte (368, 190), plutôt que
  de laisser chaque diffusion suivante retenter l'appel (cf. roadmap).
- Reprendre par glisser-déposer une pastille en échec vers un autre jour.
