# Système de notifications côté front

## Contexte

La maquette Figma de référence
([AgoraPilot](https://www.figma.com/make/r5V5mD0doadtBEFYxHeNO9/AgoraPilot))
affiche ses retours transitoires avec `sonner`, le composant toast de shadcn.
Son wrapper `src/app/components/ui/sonner.tsx` fixe trois choses : la position
(`top-right`), la palette (`richColors`), la croix de fermeture (`closeButton`),
et surcharge les animations d'entrée et de sortie par un glissement latéral.

Le front n'avait rien de tel : la page Publications portait son propre message
« maison » (`notice`, un `<p class="publications-notice">` inséré dans le flux),
affiché entre autres quand on clique sur une action dont l'endpoint back
n'existe pas encore — « Cette action n'est pas encore disponible. » Chaque page
qui aurait eu besoin d'un retour aurait dû réinventer le même bloc.

## Changements

### `core/notifications` (nouveau)

| Fichier | Rôle |
|---|---|
| `notification.model.ts` | `NotificationLevel`, `NotificationInput`, `AppNotification` et les constantes de durée, de pile et d'empilement |
| `notification.service.ts` | la pile de notifications, service global (`providedIn: 'root'`) |
| `notification-portal.service.ts` | monte le conteneur dans `<body>` |
| `notifications.providers.ts` | `provideNotifications()`, à déclarer dans `appConfig` |
| `components/notification-container` | la pile : ancrage, empilement, dépliage |
| `components/notification-toast` | le rendu d'une notification (style, animations, ARIA) |

### `NotificationService`

```ts
sendNotification({ level, message, title?, duration?, dismissible? }): number
closeNotification(id: number): void
closeAll(): void
success(message, title?) / info(...) / warning(...) / error(...)
```

- `notifications` — signal en lecture seule contenant la pile, du plus ancien au
  plus récent ; `count` compte celles encore visibles.
- `sendNotification()` renvoie l'identifiant, ce qui permet de fermer une
  notification avant la fin de sa durée d'affichage (5 s par défaut ; `0` la
  garde jusqu'à fermeture manuelle).
- `closeNotification()` ne retire pas immédiatement : la notification est
  marquée `closing` le temps de l'animation de sortie (220 ms), puis retirée.
  C'est ce qui permet de jouer la sortie sans logique d'animation dans le
  composant.
- Au-delà de **3** notifications empilées — le `visibleToasts` par défaut de
  sonner — les plus anciennes se ferment d'elles-mêmes.

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

### Le style, repris de `sonner.tsx`

| Point | Valeur |
|---|---|
| Ancrage | haut-droite, 32 px (`position="top-right"`) |
| Ordre | la plus récente en tête de pile |
| Fond | teinté du niveau — palette `richColors` de sonner |
| Largeur · écart | 356 px · 14 px |
| Rayon · typo | 8 px · 13 px / 18 px |
| Croix | ronde, en haut à droite, à `.375rem` du bord |
| Entrée | 320 ms `cubic-bezier(.16, 1, .3, 1)`, glissée depuis la droite |
| Sortie | 220 ms `ease-in`, repart vers la droite |

Palette `richColors` reportée en dur dans la feuille du toast (fond, bordure et
texte pour chacun des quatre niveaux) : ce sont des couleurs propres au
composant, sans équivalent dans les tokens de `styles.css`.

Accessibilité : le conteneur est une `region` intitulée « Notifications » ; les
toasts `error` et `warning` portent `role="alert"` (interruption), les `success`
et `info` `role="status"`. `prefers-reduced-motion` neutralise animations et
transitions.

### L'empilement en accordéon

Reprise du comportement par défaut de sonner (`expand` à `false`) : au repos,
seule la notification de tête est entièrement visible, les précédentes dépassent
derrière ; au survol, ou dès qu'une croix prend le focus au clavier, la pile se
déplie et chacune reprend sa hauteur.

Le conteneur calcule les positions au lieu de les laisser à un flux normal —
passer d'un état à l'autre doit s'animer, ce qu'un changement de mise en page ne
permet pas :

1. **Mesure** — la hauteur naturelle de chaque notification est relevée une
   fois, à l'insertion, avant qu'on lui en impose une (`afterEveryRender` +
   `offsetHeight`, qui ignore les transformations), et mémorisée par identifiant.
2. **Replié** — `translateY(profondeur × 14px) scale(1 − profondeur × 0.05)`,
   la profondeur comptant à rebours depuis la tête. Les notifications de
   derrière sont rabotées à la hauteur de celle de tête, sinon une notification
   plus haute dépasserait par le bas.
3. **Déplié** — chacune descend de ce que les précédentes occupent réellement et
   reprend sa hauteur ; la hauteur du conteneur suit, d'où une transition
   continue entre les deux états.

L'hôte du toast porte sa place dans la pile, le `.toast` intérieur porte
l'entrée et la sortie : deux éléments, donc deux `transform` qui se composent au
lieu de se marcher dessus. Sans cette séparation, l'animation d'entrée écraserait
le décalage d'empilement.

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
  commentaire est présent des deux côtés. Même chose pour
  `NOTIFICATION_STACK_GAP_PX` et l'écart de la pile.
- L'override de `sonner.tsx` déplace la croix sans rien dire de sa visibilité.
  Elle est **visible en permanence**, ce qui est le sens de l'avoir placée à
  l'intérieur du toast. Choix validé ; revenir à une croix révélée au survol
  tiendrait en une règle CSS.
- La feuille du toast frôle le budget CSS par composant (2 kB) : quelques règles
  sont volontairement factorisées.

## Vérification

- `cd apps/front && npm test` → 22 fichiers, **142 tests** verts, dont 43
  nouveaux : `notification.service.spec.ts` (18), `notification-toast` (11),
  `notification-container` (9, dont l'empilement et le dépliage),
  `notification-portal.service` (5). Le spec de `publications-page` vérifie
  désormais les retours sur le service plutôt que dans le DOM, le portal étant
  monté hors de la page testée.
- `npm run lint` → vert.
- `npm run build` → vert, sans dépassement de budget.
- Rendu des quatre niveaux, de la pile repliée et de son dépliage contrôlé dans
  le navigateur.

## Commit

_voir PR_
