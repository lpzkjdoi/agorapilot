# Développer le front en local contre le back de préproduction

**Date** : 2026-07-25
**Branche** : `claude/proxy-front-preprod`

## Contexte

La préproduction étant en service, travailler sur l'interface ne nécessite plus forcément de
faire tourner le stack Docker de développement (Postgres + `mvn spring-boot:run`) en local.
Restait à savoir si le front local pouvait joindre le back de préproduction sans bricolage.

C'est le cas, et sans aucune modification de code : depuis
[`2026-07-25-preprod-docker-compose.md`](./2026-07-25-preprod-docker-compose.md),
`environment.apiUrl` vaut `/api` et le choix du back est entièrement porté par le proxy du
serveur de développement Angular. Il ne manquait qu'un second fichier de proxy et sa
documentation.

## Changements

- `apps/front/proxy.conf.preprod.json` (nouveau) : même forme que `proxy.conf.json`, cible
  `https://preprod.chariotte-manager.fr` au lieu de `http://localhost:8080`.
- `apps/front/package.json` : script `start:preprod`
  (`ng serve --proxy-config proxy.conf.preprod.json`). `npm start` est inchangé et continue
  de viser le back local — c'est toujours le mode par défaut.
- `apps/front/README.md` : tableau des deux modes et de leurs prérequis.
- `docs/deploiement-preprod.md` : section « Développer le front en local contre le back de
  préproduction ».

Aucun composant n'est touché, donc aucun `*.spec.ts` à ajouter ou mettre à jour.

### Points de conception

**`changeOrigin: true` est indispensable.** Sans lui, le proxy transmet l'en-tête
`Host: localhost:4200` et la règle `Host(...)` du routeur Traefik ne correspond plus : la
requête n'est pas routée vers le conteneur front de la préproduction.

**Aucun CORS n'entre en jeu.** Le proxy de `ng serve` s'exécute côté Node, pas dans le
navigateur : celui-ci ne voit jamais que `localhost:4200`. Le `FRONTEND_URL` passé au back en
préproduction et la `CorsConfigurationSource` de `SecurityConfig` ne sont donc pas
sollicités, et il n'y a rien à élargir côté serveur. C'est aussi ce qui évite d'avoir à
autoriser une origine de développement sur un environnement déployé.

**Le VPN reste la seule porte d'entrée.** Le middleware `agorapilot-preprod-vpn` filtre sur
l'IP source (`10.8.0.0/24` par défaut) ; hors tunnel WireGuard, tous les appels du proxy
reçoivent 403. Le mode preprod n'ouvre donc aucun accès nouveau, il réutilise celui du
navigateur.

**Le back de préproduction n'exige pas d'authentification** : `permissiveSecurityFilterChain`
couvre le profil `preprod`. Rien à gérer côté front aujourd'hui ; le jour où l'authentification
applicative arrivera, ce mode devra être revu.

## Limites assumées

Ce mode partage la base **et** la page Facebook réelle de la préproduction
(`FACEBOOK_PAGE_ID`) : une publication déclenchée depuis le front local est réellement
publiée. Il est destiné à la mise au point d'interface ; dès qu'il s'agit d'écrire des
données ou de modifier le back, le `docker-compose.yaml` de développement reste le bon outil.

L'hôte de préproduction est écrit en dur dans `proxy.conf.preprod.json`, à l'image du
`localhost:8080` de `proxy.conf.json`. Suffisant tant qu'il n'y a qu'une préproduction.

## Vérification effectuée

- `apps/front/proxy.conf.preprod.json` et `apps/front/package.json` : JSON valide
  (`node -e "require(...)"`), et le script `start:preprod` résolu par `npm run`.
- Cohérence de la cible du proxy avec `PREPROD_HOST` et avec les règles Traefik de
  `docker-compose.preprod.yaml` vérifiée par relecture.

### Non vérifié sur le poste de développement

`npm run start:preprod` n'a pas été exécuté bout en bout : cela suppose une session VPN
WireGuard active, hors de portée d'une vérification automatisée ici. Les tests unitaires
front (Karma) n'ont pas pu tourner non plus, `CHROME_BIN` étant absent de cette machine ;
aucun fichier `.ts` de l'application n'est modifié par ce changement, et **Front CI** les
exécute au push.
