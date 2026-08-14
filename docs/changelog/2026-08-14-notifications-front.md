# Système de notifications côté front

## Contexte

La maquette Figma de référence
([AgoraPilot](https://www.figma.com/make/r5V5mD0doadtBEFYxHeNO9/AgoraPilot))
utilise des toasts empilés en bas à droite (composant `sonner` de shadcn dans le
projet Figma Make) pour tous ses retours transitoires.

Le front n'avait rien de tel : la page Publications portait son propre message
« maison » (`notice`, un `<p class="publications-notice">` inséré dans le flux),
affiché entre autres quand on clique sur une action dont l'endpoint back
n'existe pas encore — « Cette action n'est pas encore disponible. » Chaque page
qui aurait eu besoin d'un retour aurait dû réinventer le même bloc.

## Changements

### `core/notifications` (nouveau)

| Fichier | Rôle |
|---|---|
| `notification.model.ts` | `NotificationLevel`, `NotificationInput`, `AppNotification` et les constantes de durée / de taille de pile |
| `notification.service.ts` | la pile de notifications, service global (`providedIn: 'root'`) |
| `notification-portal.service.ts` | monte le conteneur dans `<body>` |
| `notifications.providers.ts` | `provideNotifications()`, à déclarer dans `appConfig` |
| `components/notification-container` | la pile de toasts (positionnement, empilement) |
| `components/notification-toast` | le rendu d'une notification (style, animations, ARIA) |

### `NotificationService`

```ts
sendNotification({ level, message, title?, duration?, dismissible? }): number
closeNotification(id: number): void
closeAll(): void
success(message, title?) / info(...) / warning(...) / error(...)
```

- `notifications` — signal en lecture seule contenant la pile, du plus ancien au
  plus récent (les notifications s'empilent, comme sur la maquette) ; `count`
  compte celles encore visibles.
- `sendNotification()` renvoie l'identifiant, ce qui permet de fermer une
  notification avant la fin de sa durée d'affichage (5 s par défaut ; `0` la
  garde jusqu'à fermeture manuelle).
- `closeNotification()` ne retire pas immédiatement : la notification est
  marquée `closing` le temps de l'animation de sortie (200 ms), puis retirée.
  C'est ce qui permet de jouer la sortie sans logique d'animation dans le
  composant.
- Au-delà de **4** notifications empilées, les plus anciennes se ferment
  d'elles-mêmes pour ne pas recouvrir la page.

### Le portal

Angular n'a pas de `createPortal` natif. La notion existe bien sous ce nom dans
le CDK (`@angular/cdk/portal`, `Overlay`), qui n'est pas une dépendance du
projet ; elle est donc reproduite avec les API du framework :
`createComponent()` instancie `NotificationContainerComponent` sur un élément
hôte créé à la volée, `ApplicationRef.attachView()` le branche sur la détection
de changement, et l'hôte est ajouté à `<body>`.

Conséquence : la pile vit en dehors de l'arborescence du composant qui déclenche
la notification, donc à l'abri des `overflow`, `transform` et `z-index` locaux —
elle passe notamment au-dessus de la modale de création de publication. Aucun
composant à placer dans un template : `provideNotifications()` dans
`app.config.ts` suffit, le service est ensuite injectable partout.

### Style et animations

Repris de la maquette : carte blanche, bordure fine, `--shadow-hover`, coins
`--radius-md`, filet de 3 px teinté du niveau à gauche, icône Material dans une
pastille teintée, croix de fermeture. Pile ancrée en bas à droite, la plus
récente en bas, `gap` de 0,75 rem, pleine largeur sous 640 px.

Couleurs prises sur les tokens existants de `styles.css` : `--color-status-success`,
`--color-brand-blue`, `--color-status-pending`, `--color-status-danger`.

Animations en CSS (pas de `@angular/animations`) : entrée en 260 ms
(`cubic-bezier(.21, 1.02, .73, 1)`, translation verticale + léger `scale`),
sortie en 200 ms vers la droite. `prefers-reduced-motion` les neutralise.

Accessibilité : le conteneur est une `region` intitulée « Notifications » ; les
toasts `error` et `warning` portent `role="alert"` (interruption), les `success`
et `info` `role="status"`.

### Page Publications

Le `notice` maison disparaît (signal, bloc de template et styles associés) au
profit du service — c'est le remplacement demandé pour les trois actions sans
endpoint, et par cohérence pour les deux retours de création :

| Retour | Avant | Après |
|---|---|---|
| Action sans endpoint | `notice.set(…)` | `notifications.warning('Cette action n'est pas encore disponible.')` |
| Publication créée | `notice.set(…)` | `notifications.success('Publication créée.')` |
| Création en échec | `notice.set(…)` | `notifications.error('La publication n'a pas pu être créée.')` |

L'erreur de **chargement** de la liste reste, elle, un bloc dans le flux
(`.publications-error`) : ce n'est pas un retour transitoire mais un état de la
page, qui remplace la grille.

## Points d'attention

- Le type s'appelle `AppNotification` et non `Notification` : ce dernier est le
  type global de l'API Web Notifications, et le masquer dans chaque fichier qui
  importe le modèle rend les erreurs de typage illisibles.
- `NOTIFICATION_EXIT_DURATION_MS` (service) et la durée de l'animation
  `notification-leave` (CSS du toast) doivent rester synchronisées ; le
  commentaire est présent des deux côtés.
- La feuille du toast frôle le budget CSS par composant (2 kB) : quelques
  règles sont volontairement factorisées.

## Vérification

- `cd apps/front && npm test` → 22 fichiers, **139 tests** verts, dont 40
  nouveaux : `notification.service.spec.ts` (18), `notification-toast` (11),
  `notification-container` (6), `notification-portal.service` (5). Le spec de
  `publications-page` vérifie désormais les retours sur le service plutôt que
  dans le DOM, le portal étant monté hors de la page testée.
- `npm run lint` → vert.
- `npm run build` → vert, sans dépassement de budget.
- Rendu des quatre niveaux (avec et sans titre) contrôlé dans le navigateur et
  comparé aux toasts de la maquette.

## Commit

_voir PR_
