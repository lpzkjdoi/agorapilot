# Suspendre le canal Facebook quand le compte est bloqué

- **Commits** : [`03132b1`](https://github.com/lpzkjdoi/agorapilot/commit/03132b13f01fbe4c46a7ac9456ffdabf44adb2ba) (back),
  [`1c15101`](https://github.com/lpzkjdoi/agorapilot/commit/1c151011e1a70c409806520b219686522fada23a) (front),
  [`528e7b4`](https://github.com/lpzkjdoi/agorapilot/commit/528e7b48eb7888fb9532dc17e0f5adcbb121f13c) (test des campagnes)

## Contexte

Suite directe de la [reprise d'une diffusion en échec](./2026-09-26-reprise-diffusion-echec.md).
Le 26/09, Facebook a bloqué la Page (code 368, confirmation d'identité de
l'administrateur). Chaque diffusion suivante a retenté l'appel et échoué de la
même façon. Le calendrier s'est rempli d'échecs identiques, qu'il fallait
ensuite reprendre un par un.

Deux codes de l'API Graph disent que le **compte** ne peut plus publier, quel
que soit le contenu :

- **368** : action bloquée (confirmation d'identité, restriction temporaire) ;
- **190** : jeton invalide ou révoqué.

Tant que personne n'est intervenu sur Facebook, retenter ne sert à rien. Insister
sur un compte bloqué peut même prolonger le blocage.

## Changements

### Suspension (back)

- `GraphApiError.blocksAccount()` : vrai pour les codes 190 et 368.
- Nouveau paquet `publisher/suspension` :
  - entité `ChannelSuspension`, table `channel_suspensions`, une ligne au plus
    par canal (le canal sert de clé) : motif et date de la suspension. Elle est
    **persistée** : un compte bloqué le reste après un redémarrage du back, et
    c'est justement au redémarrage que l'ordonnanceur retenterait tout ce qui
    attend. La table est créée par `ddl-auto: update`, **aucune migration
    manuelle** ;
  - `ChannelSuspensionService` : `suspend`, `lift`, `ensureNotSuspended`. Une
    suspension déjà en place est conservée telle quelle : c'est le premier refus
    qui explique le blocage, et sa date dit depuis quand les diffusions attendent.
- `FacebookPublisher` : sur un refus 190 ou 368, la livraison passe `FAILED`
  comme avant, **et** le canal est suspendu avec le motif lisible.

### Effet de la suspension

- **Ordonnanceur** : une livraison due sur un canal suspendu reste `PENDING`,
  **sans appel** à Facebook. L'occurrence reste `SCHEDULED` et revient à chaque
  balayage :
  - si la suspension est levée à temps, la diffusion part normalement au
    balayage suivant ;
  - au-delà du retard maximal (`max-lateness`, 1 h par défaut), elle passe en
    échec avec un motif qui explique l'attente :
    `Non diffusée : Diffusions Facebook suspendues depuis le 16/10/2026 à 14:05 : Confirmez votre identité… (Facebook, code 368) — plus de 60 minutes de retard depuis.`
    Elle se reprend ensuite avec le bouton « Reprendre » du calendrier.
- **« Publier maintenant »** : refusé en `409`, sans rien tenter ni créer
  d'occurrence, avec le motif de la suspension.

Pourquoi garder les diffusions en attente plutôt que de les passer en échec tout
de suite : si le problème est réglé vite (identité confirmée dans l'heure), les
diffusions du jour partent d'elles-mêmes, sans reprise une par une.

### Levée (API)

- `GET /api/channels/suspensions` : les canaux suspendus, liste vide sinon.
- `DELETE /api/channels/{channel}/suspension` : lève la suspension, `204`,
  idempotent.

Sous `/api` et non `/admin` : le bandeau du front doit pouvoir les joindre.

La levée est **toujours manuelle**. Rien côté AgoraPilot ne sait dire si
l'identité a été confirmée sur Facebook, ou si un nouveau jeton est en place.

### Bandeau (front)

- `ChannelSuspensionsService` (`core/channel-suspensions`) : état partagé des
  suspensions, `refresh()` et `lift()`.
- `ChannelSuspensionBannerComponent`, monté dans `app.component` au-dessus du
  contenu de **toutes les pages** : canal, date (« depuis le vendredi 16 octobre
  à 14:05 »), motif de Facebook, explication et bouton **« Reprendre les
  diffusions »**.
- Le bandeau interroge le back **chaque minute**, la période de balayage de
  l'ordonnanceur : une suspension survient sans action de l'utilisateur. Une
  lecture en échec garde le dernier état connu, sans notification.
- La page Publications rafraîchit les suspensions dès qu'un « Publier
  maintenant » échoue. Le bandeau apparaît aussitôt si ce refus vient de
  suspendre le canal, ou s'il s'est heurté à une suspension.

La maquette Figma ne prévoit pas de bandeau de suspension. Le bandeau reprend la
structure de son composant `Alert` (`ui/alert.tsx`) dans la palette « warning »
des notifications.

### Correctif annexe : test daté du service des campagnes

`CampaignServiceTest` fixait la date de début de sa campagne au 1er octobre
2026, en supposant qu'elle était dans le futur. Une fois cette date passée, le
statut dérivé est devenu `ACTIVE` au lieu de `SCHEDULED`. **Le Back CI échoue
sur `develop` depuis le 1er octobre** (2 tests). Les dates sont désormais
relatives à aujourd'hui (J+10 et J+40).

## Vérification

```bash
cd apps/back && mvn -B verify   # 276 tests, BUILD SUCCESS
cd apps/front && npm test       # 47 fichiers, 433 tests
cd apps/front && npm run lint   # All files pass linting
cd apps/front && npx ng build   # OK
```

Tests ajoutés :

- `GraphApiErrorTest` : 190 et 368 bloquent le compte, un 100 ou une erreur
  sans code non.
- `FacebookPublisherTest` : suspension sur 368 et sur 190 avec le motif lisible,
  aucune suspension sur un refus propre au post (100) ni sur une coupure réseau.
- `ChannelSuspensionServiceTest` (**nouveau**, 6) : motif et date enregistrés,
  première suspension conservée, levée, levée sans suspension, refus d'une
  diffusion immédiate avec le message complet.
- `ChannelSuspensionControllerTest` (**nouveau**, 4) : liste, liste vide, levée
  `204`, canal inconnu `400`.
- `PublicationDeliveryServiceTest` : « Publier maintenant » refusé sans rien
  créer ni appeler, livraison retenue sans appel, échec au-delà du retard
  maximal avec le motif de la suspension.
- `PublicationControllerDeliveryTest` : `409` avec le motif.
- `ScheduledDeliveryPersistenceTest` (H2, vrai publisher) : **le scénario du
  26/09 de bout en bout**. Un refus 368 suspend le canal, l'occurrence suivante
  attend sans appel, puis part après la levée.
- Front : service (**nouveau**, 4), bandeau (**nouveau**, 6 : rien quand tout
  va bien, contenu, interrogation périodique, échec de lecture silencieux, levée
  réussie, levée en échec), page Publications (`409` relayé et bandeau
  rafraîchi), `AppComponent` (bandeau monté).

Vérifié aussi dans le navigateur, contre une API bouchonnée : bandeau sur le
tableau de bord et le calendrier, disparition après « Reprendre les
diffusions ».

## Limites connues

- **Levée manuelle uniquement** : un nouveau jeton posé par
  `POST /admin/facebook/bootstrap` ne lève pas une suspension pour code 190. Il
  faut aussi cliquer « Reprendre les diffusions ».
- **Diffusions retenues** : passée leur heure, elles restent « programmées »
  dans le calendrier, et ne peuvent être ni déplacées ni annulées tant qu'elles
  n'ont pas été tranchées (levée, ou échec au-delà du retard maximal). Le
  bandeau explique la situation.
- **Pas d'alerte en dehors de l'application** (mail, push) : la suspension ne se
  voit qu'en ouvrant AgoraPilot.

## Reste à faire

- Lever automatiquement une suspension pour code 190 après un bootstrap réussi
  du jeton.
- Prévenir par mail quand un canal est suspendu, une fois la gestion des
  utilisateurs en place (destinataires).
