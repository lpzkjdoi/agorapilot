# Front

This project was generated with [Angular CLI](https://github.com/angular/angular-cli) version 18.0.1.

## Development server

Run `ng serve` for a dev server. Navigate to `http://localhost:4200/`. The application will automatically reload if you
change any of the source files.

Le front appelle l'API en relatif (`/api`) : c'est le proxy du serveur de
développement qui décide quel back est joint.

| Commande | Back visé | Prérequis |
|---|---|---|
| `npm start` | `http://localhost:8080` (`docker-compose.yaml` à la racine) | stack Docker de dev démarré |
| `npm run start:preprod` | `https://preprod.chariotte-manager.fr` | **connexion au VPN WireGuard** |

Le mode préproduction lit et écrit les **vraies données** de la préproduction, y
compris la page Facebook associée. Voir
[`docs/deploiement-preprod.md`](../../docs/deploiement-preprod.md#développer-le-front-en-local-contre-le-back-de-préproduction).

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can also use
`ng generate directive|pipe|service|class|guard|interface|enum|module`.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory.

## Running unit tests

Les tests unitaires tournent sous [Vitest](https://vitest.dev), via le builder
`@angular/build:unit-test` et un environnement jsdom : aucun navigateur n'est
nécessaire.

| Commande | Effet |
|---|---|
| `npm test` | exécute la suite une fois (mode surveillance désactivé hors TTY) |
| `npm run test:watch` | relance les tests concernés à chaque modification |
| `npm run test:coverage` | ajoute un rapport de couverture (provider v8) |

Les globales `describe` / `it` / `expect` / `vi` sont injectées par le builder :
aucun import n'est nécessaire dans les specs.

## Running end-to-end tests

Run `ng e2e` to execute the end-to-end tests via a platform of your choice. To use this command, you need to first add a
package that implements end-to-end testing capabilities.

## Further help

To get more help on the Angular CLI use `ng help` or go check out
the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
