# Notifications

Le back prévient l'équipe de ce qui se passe sur les diffusions : programme du
jour chaque matin, échec d'une diffusion. Les messages partent aujourd'hui dans
des salons Discord, par webhook. Le module est fait pour accueillir d'autres
canaux, ou en retirer.

Code : `apps/back/src/main/java/.../notification`.

## Ce qui est notifié

| Type | Quand | Salon |
|---|---|---|
| `DAILY_SUMMARY` | Chaque jour à 7h (`NOTIFICATIONS_DAILY_SUMMARY_CRON`, fuseau `TZ`). Les diffusions du jour dans l'ordre, les canaux, les échecs de la veille. Envoyé même s'il n'y a aucune diffusion : son absence signale une panne. | `#programme-du-jour` |
| `DELIVERY_FAILED` | Une occurrence passe en échec, qu'elle soit planifiée ou lancée par « publier maintenant ». Un message par occurrence, avec le motif de chaque canal en échec. | `#alertes` |
| `TEST` | Envoi manuel (voir plus bas). | `#essais-notifications` |

Chaque message porte le nom de l'environnement (`AgoraPilot · preprod`), dans
le nom de l'expéditeur et dans le pied de la carte.

## Architecture

```
Code métier                              notification/
──────────────                           ───────────────────────────────────────
refreshStatus() ── OccurrenceFailedEvent ─► DeliveryFailureNotifier ─┐
DailySummaryScheduler (cron) ── DailySummaryService ─────────────────┤
NotificationAdminController (/admin) ────────────────────────────────┤
                                                                      ▼
                                   NotificationService ─► NotificationSenderFactory
                                   (routage par type)        ├─ DiscordNotificationSender
                                                             └─ WebhookNotificationSender
```

- **`Notification`** : le modèle neutre, en texte brut (titre, résumé, lignes
  horodatées, champs, lien, gravité). Le code métier ne connaît aucun canal.
- **`NotificationService`** : envoie la notification aux cibles qui suivent son
  type. L'échec d'une cible est journalisé, sans bloquer les autres ni le
  traitement métier. L'URL du webhook est masquée dans les messages d'erreur.
- **`NotificationSenderFactory`** : fournit le sender d'une technologie. Les
  senders sont découverts par injection, comme les publishers.
- **`DiscordNotificationSender`** et **`DiscordEmbedRenderer`** : la carte
  Discord (embed). Couleur selon la gravité, heures en `<t:…>` (affichées dans
  le fuseau du lecteur), texte utilisateur échappé, `allowed_mentions` vide pour
  qu'aucun « @everyone » ne pingue, limites de Discord appliquées. Sur un 429,
  une seule nouvelle tentative.
- **`WebhookNotificationSender`** : le modèle tel quel, en JSON, pour n8n, Home
  Assistant, etc.
- **Échecs** : `PublicationOccurrenceService.refreshStatus` publie
  `OccurrenceFailedEvent` au seul passage en `FAILED`. L'écouteur s'exécute
  après validation de la transaction et sur un autre fil (`@Async`) : un
  Discord lent ne retient pas l'ordonnanceur.

## Configuration

Les cibles sont déclarées dans `application.yaml` (`agorapilot.notifications`).
**Les URL de webhook n'y figurent pas** : elles contiennent le jeton du webhook,
et le dépôt est public. Elles viennent de l'environnement, et une URL vide
désactive la cible. En développement local et dans les tests, rien ne part
donc par défaut.

| Variable | Rôle |
|---|---|
| `NOTIFICATIONS_DISCORD_PROGRAMME_URL` | Webhook de `#programme-du-jour` |
| `NOTIFICATIONS_DISCORD_ALERTES_URL` | Webhook de `#alertes` |
| `NOTIFICATIONS_DISCORD_ESSAIS_URL` | Webhook de `#essais-notifications` |
| `NOTIFICATIONS_ENABLED` | Interrupteur général (`true` par défaut) |
| `NOTIFICATIONS_ENVIRONMENT` | Nom affiché ; par défaut, le profil Spring actif |
| `NOTIFICATIONS_DAILY_SUMMARY_CRON` | Heure du programme du jour (`0 0 7 * * *`) |

En préproduction, les trois URL sont des **secrets** de l'Environment GitHub
`preprod`, recopiés dans le `.env` du VPS à chaque déploiement.

### Créer un webhook Discord

*Modifier le salon → Intégrations → Webhooks → Nouveau webhook*, puis *Copier
l'URL du webhook*. Le nom et l'avatar choisis dans Discord n'ont pas
d'importance : le back les remplace. Si une URL fuit, supprimez le webhook et
recréez-en un : l'ancienne URL cesse de fonctionner.

## Vérifier

Les endpoints `/admin/notifications/**` exigent le droit `channels:manage`. Ils
ne sont pas exposés par le front : on les appelle depuis le VPS, sur
`127.0.0.1:8080`.

| Endpoint | Effet |
|---|---|
| `GET /admin/notifications/targets` | Les cibles, leur type, les événements suivis, et si elles sont configurées (jamais l'URL) |
| `POST /admin/notifications/test` | Une carte de test aux cibles qui suivent `TEST` |
| `POST /admin/notifications/test?target=alertes` | Une carte de test à cette cible seule |
| `POST /admin/notifications/daily-summary` | Le programme du jour, envoyé tout de suite |

Chaque envoi renvoie, par cible, `delivered` et le motif d'un éventuel échec.

## Ajouter un canal ou un événement

- **Canal** (e-mail, Slack…) : une valeur dans `NotificationChannelType`, et un
  `@Component` qui implémente `NotificationSender`. La factory le découvre
  seule. Il ne reste qu'à déclarer une cible avec ce `type`.
- **Événement** (canal suspendu, jeton Facebook expiré…) : une valeur dans
  `NotificationType`, une icône dans `DiscordEmbedRenderer.icon`, puis l'appel
  à `NotificationService.notify` là où l'événement se produit, de préférence
  via un événement Spring. Enfin, l'ajouter aux `events` de la cible voulue.
