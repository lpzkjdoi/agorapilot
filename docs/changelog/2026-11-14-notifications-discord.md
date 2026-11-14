# Notifications Discord : programme du jour et échecs de diffusion

**Date** : 2026-11-14
**Périmètre** : `apps/back`, déploiement de préproduction

## Contexte

Aucun signal ne sortait de l'application : un échec de diffusion ne se voyait
qu'en ouvrant le calendrier, et rien ne rappelait le programme du jour. Il
fallait un service de notification, branché d'abord sur des salons Discord,
mais qui accepte d'autres canaux (en plus ou en moins) sans toucher au code
métier.

## Changements

### Module `notification`

- **Modèle neutre** `Notification` (type, gravité, titre, résumé, lignes
  horodatées, champs, lien, référence), en texte brut.
- **Factory** `NotificationSenderFactory` : résout le `NotificationSender`
  d'une technologie. Les senders sont découverts par injection, sur le modèle
  de `PublisherRegistry`.
- **Senders** :
  - `DiscordNotificationSender` : carte (embed) mise en forme par
    `DiscordEmbedRenderer`, avec la couleur de la gravité, les heures en
    `<t:…>`, le texte utilisateur échappé, `allowed_mentions` vide, les limites
    de Discord appliquées et une nouvelle tentative sur 429. L'appel se fait en
    `?wait=true` pour qu'un refus remonte.
  - `WebhookNotificationSender` : le modèle brut, en JSON.
- **`NotificationService`** : envoie aux cibles qui suivent le type. Chaque
  cible est isolée des autres, et l'URL du webhook est masquée dans les erreurs
  et les logs.
- **Client HTTP dédié** (`NotificationRestClient`), avec des délais courts. Il
  est enveloppé dans son propre type pour ne pas entrer en concurrence avec le
  `RestClient` de Facebook, injecté par type.

### Déclencheurs

- **Échec de diffusion** : `PublicationOccurrenceService.refreshStatus` publie
  `OccurrenceFailedEvent` au passage en `FAILED`, et seulement à ce moment-là :
  pas de nouvelle notification tant que l'occurrence reste en échec.
  `DeliveryFailureNotifier` écoute après validation de la transaction, en
  `@Async`. Il couvre la diffusion planifiée comme « publier maintenant ».
- **Programme du jour** : `DailySummaryScheduler`, à 7h par défaut. Il liste
  les diffusions du jour (hors publications archivées) avec leurs canaux et
  leur campagne, et rappelle les échecs de la veille (gravité WARNING s'il y en
  a). Il part même sans diffusion.
- **Administration** : `GET /admin/notifications/targets`,
  `POST /admin/notifications/test[?target=…]` et
  `POST /admin/notifications/daily-summary`, sous `channels:manage`.

### Configuration

- `application.yaml` déclare trois cibles Discord : `programme-du-jour`,
  `alertes` et `essais-notifications`. Les URL viennent de
  `NOTIFICATIONS_DISCORD_PROGRAMME_URL`, `NOTIFICATIONS_DISCORD_ALERTES_URL` et
  `NOTIFICATIONS_DISCORD_ESSAIS_URL`. Elles ne sont pas en dur, parce que le
  dépôt est public et que l'URL porte le jeton du webhook. Sans URL, une cible
  est ignorée.
- Le nom de l'environnement (le profil Spring par défaut) apparaît sur chaque
  message.
- `docker-compose.preprod.yaml`, `.env.preprod.example` et le workflow de
  déploiement transmettent les trois variables. Ce sont des secrets facultatifs
  de l'Environment GitHub `preprod`.

Documentation de référence : [`docs/notifications.md`](../notifications.md).

## Vérification

- `mvn -B verify` : 622 tests, tous verts.
- Nouveaux tests unitaires : factory, routage et isolement des cibles, masquage
  de l'URL, rendu Discord (couleurs, horodatages, échappement, limites), corps
  HTTP exact des deux senders (`MockRestServiceServer`), nouvelle tentative sur
  429, contenu du programme du jour et de l'alerte d'échec, émission de
  l'événement au seul passage en échec, contrôleur d'administration.
- `NotificationFlowTest` (base H2, vraies transactions) : une diffusion
  planifiée en échec arrive bien au service de notification, après validation
  et sur le fil asynchrone. Le test vérifie aussi que les trois cibles sont
  bien lues depuis `application.yaml`.
- `EndpointPermissionsTest` : les trois endpoints sont réservés à
  `channels:manage`.

## Mise en service

- Les trois URL sont enregistrées comme **secrets** de l'Environment GitHub
  `preprod` (2026-11-14, `gh secret set` en passant la valeur par l'entrée
  standard). Elles seront prises en compte au premier déploiement qui suit la
  fusion. Procédure de remplacement et de vérification : runbook, section
  [Notifications Discord](../deploiement-preprod.md#notifications-discord).
- Rendu vérifié dans un vrai Discord : une carte de test, produite par le vrai
  renderer et le vrai sender, a été acceptée et s'affiche correctement dans
  `#essais-notifications`. Le texte échappé (`essais-notifications`) apparaît
  sans barre oblique. Les heures `<t:…>` et les sous-lignes `-#` du programme
  du jour n'ont pas encore été vues en réel.

## Commit

_voir PR_
