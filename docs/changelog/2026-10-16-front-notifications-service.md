# Ajout du `NotificationsService` manquant (build front cassé)

- **Date** : 2026-10-16
- **Commit** : [`042aa79`](https://github.com/lpzkjdoi/agorapilot/commit/042aa7922f84845f830099dcd6e156678e948fa0)
- **Branche** : `claude/inspiring-cannon-e271ay`
- **Périmètre** : `apps/front`

## Contexte

`dashboard-page.ts` importait un `NotificationsService` (`core/notifications/notifications.service`) qui n'a jamais existé dans l'historique Git : le front ne compilait plus (`TS2307: Cannot find module`). Déjà relevé comme point hors périmètre lors de la suppression de Tailwind. L'absence de CI front explique que ce soit passé inaperçu.

Plutôt que de retirer l'appel, le service a été réellement implémenté : l'intention du dashboard (signaler à l'utilisateur l'échec du chargement des occurrences) est légitime et le service servira aux prochaines fonctionnalités (formulaire de publication, etc.).

## Changements

- **`core/notifications/notifications.service.ts`** — service `providedIn: 'root'` à base de signal :
  - `send(content, level?)` accepte un message texte (niveau `info` par défaut) ou une erreur quelconque (niveau `error` par défaut) convertie en message lisible :
    - `HttpErrorResponse` statut 0 → « Impossible de joindre le serveur. »
    - `HttpErrorResponse` avec un corps `{ message }` (format `ApiError` du back) → ce message
    - autre `HttpErrorResponse` → « Erreur serveur (<statut>). »
    - `Error` → son message ; sinon message générique
  - fermeture automatique après 5 s, et `dismiss(id)` pour une fermeture manuelle.
- **`core/notifications/notifications-container/`** — composant de toasts (bas-droite, bordure colorée selon le niveau via les tokens `--color-status-*`, bouton de fermeture, `aria-live="polite"`), monté une fois dans `app.component.html`.
- **`notifications.service.spec.ts`** — 6 tests unitaires (conversion des erreurs, fermeture manuelle, fermeture auto).

## Vérification

- `ng build` dev et production : échec reproduit avant correctif, build propre après.
- `ng test` (Chromium headless) : les 6 nouveaux tests passent.
- Vérification visuelle via Chromium/Playwright sur `/dashboard`, avec l'API simulée par interception réseau : serveur injoignable, erreur 500 sans corps, erreur 404 avec `{ message }`. Toast affiché avec le bon message dans les trois cas, fermeture manuelle et automatique (5 s) vérifiées.

## Points notables (hors périmètre)

- `app.component.spec.ts` (spec générée par Angular, jamais mise à jour) échoue sur ses 3 tests : `provideRouter` manquant (la sidebar utilise `RouterLink`) et assertion sur un titre « Hello, front » qui n'existe plus. Ces tests ne pouvaient pas tourner jusqu'ici puisque le build échouait.
- `ApiError` côté back n'a que des setters Lombok (`@Setter`), pas de getters : Jackson ne peut pas le sérialiser, donc les handlers de `GlobalExceptionHandler` ne renvoient probablement pas le `{ message, code }` attendu. Le front retombe alors sur le message générique « Erreur serveur (<statut>). »
