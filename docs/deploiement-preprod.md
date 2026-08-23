# Déploiement de la préproduction (VPS)

Runbook de la préproduction. Le déploiement est **automatique à chaque fusion sur
`develop`** (cf. [Déploiement automatique](#déploiement-automatique)) ; la
procédure manuelle décrite ici reste le mode de secours et la référence pour
comprendre le montage, que la CI reprend à l'identique.

## Vue d'ensemble

Trois conteneurs, décrits par [`docker-compose.preprod.yaml`](../docker-compose.preprod.yaml) :

| Service | Rôle | Exposition |
|---|---|---|
| `front` | nginx : sert la SPA Angular **et** proxifie `/api` vers le back | Réseau `web` → Traefik → `https://$PREPROD_HOST`, **restreint au VPN** |
| `back` | Spring Boot, profil `preprod`, volume `media-data` | Réseau interne + `127.0.0.1:8080` sur le VPS |
| `db` | PostgreSQL 16, volume `db-data` | Réseau interne uniquement |

Trois conséquences de ce montage :

- **Une seule origine.** Le front appelle `/api` en relatif, nginx relaie vers le
  back. Aucun CORS à configurer, et aucune URL d'API à substituer d'un
  environnement à l'autre. L'image, elle, est propre à son environnement depuis
  le badge du bandeau (`build-info.ts`, figé au build) : la prod aura son propre
  build du front, ce que fera de toute façon un workflow déclenché sur tag.
- **Un seul point d'entrée public.** Seul `front` est attaché au réseau Traefik.
  La base n'est joignable par personne d'autre que le back.
- **Deux volumes à sauvegarder, pas un.** `db-data` porte la base, `media-data`
  porte les affiches déposées dans la médiathèque (monté sur
  `/var/lib/agorapilot/media`). Ni l'un ni l'autre n'est reconstructible à partir
  du dépôt : une sauvegarde qui n'emporterait que la base laisserait des
  publications référençant des visuels disparus.

## Accès restreint au VPN

La préproduction n'est joignable que depuis le réseau WireGuard du VPS
(`wg0`, `10.8.0.0/24`). Toute autre IP source reçoit **403**.

C'est un middleware Traefik `ipwhitelist` porté par le conteneur `front`, dont la
plage vient de `PREPROD_ALLOWED_CIDRS`. Trois points à retenir :

- **Le nom du middleware est `ipwhitelist`, pas `ipallowlist`.** Le second
  n'existe qu'à partir de Traefik v2.11 ; le VPS tourne en v2.10, et
  `ipwhitelist` y a été vérifié fonctionnel. À reprendre si Traefik est mis à
  niveau, le renommage étant l'inverse (`ipwhitelist` disparaît en v3).
- **Le port 443 reste joignable publiquement**, et c'est délibéré : le challenge
  TLS-ALPN de Let's Encrypt est traité pendant la poignée de main TLS, avant tout
  routage HTTP, donc le filtre ne devrait pas le bloquer. Un observateur externe
  peut en déduire que le domaine existe, mais n'obtient aucun contenu.
  ⚠️ **Point non encore vérifié en pratique** : le certificat courant a été émis
  *avant* la mise en place du filtre et n'expire que le 23 octobre 2026, donc le
  premier renouvellement sous filtre aura lieu vers fin septembre. Ce qui est
  vérifié, c'est que la poignée de main TLS aboutit toujours pour un client
  externe (le certificat est bien servi avant le 403), ce qui est la couche sur
  laquelle opère le challenge. Si le renouvellement échouait malgré tout, la
  solution est de basculer le resolver sur un challenge **DNS-01**, qui ne
  requiert aucune joignabilité publique.
- **Les clients doivent être en tunnel complet** (`AllowedIPs = 0.0.0.0/0`).
  En tunnel partagé, le trafic vers l'IP publique du VPS sortirait hors du tunnel
  et serait rejeté. Attention : ajouter simplement l'IP publique du VPS aux
  `AllowedIPs` d'un tunnel partagé crée une boucle de routage, le handshake
  WireGuard partant lui-même dans le tunnel.

Le domaine n'a **pas d'enregistrement AAAA**, ce qui est important : un tunnel
déclaré en `0.0.0.0/0` ne capte pas l'IPv6. Si un AAAA est ajouté un jour, il
faudra soit passer les clients en `0.0.0.0/0, ::/0`, soit ajouter la plage IPv6
correspondante à `PREPROD_ALLOWED_CIDRS`, sans quoi les clients sur réseau IPv6
seraient rejetés.

### Vérifier le filtrage

Hors VPN, depuis n'importe quelle machine :

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://preprod.chariotte-manager.fr
```

Attendu : **403**. Connecté au VPN, la même commande doit renvoyer **200**.

Le filtrage peut aussi être testé depuis le VPS lui-même, sans client VPN, en
choisissant l'interface source — `10.8.0.1` étant dans la plage autorisée :

```bash
curl -sS --interface 10.8.0.1 -o /dev/null -w '%{http_code}\n' https://preprod.chariotte-manager.fr
```

**Un 404 juste après un déploiement est normal et transitoire** : pendant la
recréation du conteneur `front`, Traefik n'a momentanément plus de routeur pour
cet hôte et répond 404. Attendre quelques secondes et refaire le test — un 404
persistant, lui, signale un problème de découverte (labels, réseau `web`).

## Prérequis

1. **Un enregistrement DNS A** pour le sous-domaine de préproduction, pointant
   vers l'IP du VPS. Traefik en a besoin pour router *et* pour obtenir le
   certificat Let's Encrypt (challenge TLS-ALPN).
2. **Le Traefik du VPS déjà démarré**, avec son réseau externe `web` :

   ```bash
   docker network ls --filter name=web
   ```

   S'il est absent : `docker network create web`, puis relancer Traefik.
3. **Docker et le plugin Compose** sur le VPS.

## Première installation

```bash
sudo mkdir -p /opt/agorapilot/preprod && sudo chown "$USER" /opt/agorapilot/preprod
git clone https://github.com/lpzkjdoi/agorapilot.git /opt/agorapilot/preprod/repo
```

Créer le fichier de variables à partir du contrat versionné, le renseigner, puis
le protéger :

```bash
cd /opt/agorapilot/preprod/repo && cp .env.preprod.example .env && chmod 600 .env
```

Voir [`.env.preprod.example`](../.env.preprod.example) pour le détail de chaque
variable. Les points à ne pas manquer :

- `PREPROD_HOST` : le sous-domaine, **sans** `https://`.
- `POSTGRES_PASSWORD` : à générer (`openssl rand -base64 32`). Aucune valeur par
  défaut : le déploiement échoue si elle est vide.
- Les trois variables `FACEBOOK_*` : sans elles, `docker compose up` s'arrête
  avec un message nommant la variable manquante.

Puis démarrer :

```bash
docker compose -f docker-compose.preprod.yaml up -d --build
```

> Le `--build` construit les images sur le VPS. Un build Angular + Maven est
> gourmand en RAM ; sur une petite machine, préférer attendre la publication des
> images sur GHCR par la CI, puis déployer en `pull` (voir plus bas).

## Vérifications après déploiement

```bash
docker compose -f docker-compose.preprod.yaml ps
```

Les trois services doivent être `healthy`. Ensuite, depuis le VPS :

```bash
curl -s http://127.0.0.1:8080/actuator/health
```

Attendu : `{"status":"UP"}`.

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/publications
```

Attendu : **200**. Un **401** signifierait que le profil `preprod` n'a pas de
chaîne de filtres et que Spring Security est repassé sur son HTTP Basic par
défaut (cas verrouillé par `SecurityConfigPreprodTest`).

Enfin, depuis l'extérieur : `https://$PREPROD_HOST` doit servir l'application
avec un certificat valide. Si le certificat manque, regarder les logs Traefik :

```bash
docker logs traefik 2>&1 | grep -i agorapilot
```

## Mises à jour

> Depuis la mise en place de [`deploy-preprod.yml`](#déploiement-automatique),
> les mises à jour sont automatiques à chaque fusion sur `develop`. Ce qui suit
> reste le mode de secours, quand GitHub est indisponible ou qu'il faut
> déployer une branche non fusionnée.

```bash
cd /opt/agorapilot/preprod/repo && git pull && docker compose -f docker-compose.preprod.yaml up -d --build
```

Une fois les images publiées sur GHCR, `IMAGE_TAG` renseigné dans le `.env` :

```bash
docker compose -f docker-compose.preprod.yaml pull && docker compose -f docker-compose.preprod.yaml up -d
```

## Développer le front en local contre le back de préproduction

Pour itérer sur l'interface sans faire tourner le stack Docker de développement,
`ng serve` peut proxifier `/api` vers la préproduction :

```bash
cd apps/front && npm run start:preprod
```

Le script s'appuie sur [`proxy.conf.preprod.json`](../apps/front/proxy.conf.preprod.json),
qui remplace la cible `http://localhost:8080` de
[`proxy.conf.json`](../apps/front/proxy.conf.json) par `https://$PREPROD_HOST`.
`npm start` reste inchangé et continue de viser le back local.

Trois points à connaître :

- **Le VPN est obligatoire.** Le middleware `agorapilot-preprod-vpn` filtre sur
  l'IP source ; hors tunnel WireGuard, tous les appels du proxy reçoivent 403
  (cf. [Accès restreint au VPN](#accès-restreint-au-vpn)).
- **Aucun CORS n'entre en jeu.** Le proxy de `ng serve` est côté Node : le
  navigateur ne voit que `localhost:4200`. Le `FRONTEND_URL` du back et sa
  `CorsConfigurationSource` ne sont pas sollicités. `changeOrigin: true` est en
  revanche indispensable — sans lui l'en-tête `Host` reste `localhost` et la
  règle `Host(...)` de Traefik ne route pas la requête.
- **Les données sont celles de la préproduction**, y compris la page Facebook
  réelle désignée par `FACEBOOK_PAGE_ID` : une publication déclenchée depuis le
  front local part pour de bon. Ce mode convient à la mise au point d'interface ;
  dès qu'il s'agit d'écrire, ou de modifier le back, utiliser
  [`docker-compose.yaml`](../docker-compose.yaml).

## Endpoints d'administration Facebook

`FacebookAdminController` expose `POST /admin/facebook/bootstrap` et
`GET /admin/facebook/token/status` **sans aucune authentification**. Ils ne sont
donc **pas** proxifiés par le nginx du front : depuis Internet, `/admin/...`
renvoie l'`index.html` de la SPA, jamais l'API.

Ils restent joignables sur la boucle locale du VPS. Pour initialiser le token de
page depuis un poste de travail, ouvrir un tunnel SSH :

```bash
ssh -L 8080:127.0.0.1:8080 utilisateur@vps
```

Il faut ensuite un **token utilisateur de courte durée**, généré depuis le
[Graph API Explorer](https://developers.facebook.com/tools/explorer/). Deux
précautions, faute de quoi le bootstrap échoue :

1. **Sélectionner la bonne app** dans le menu « Meta App » en haut à droite,
   celle dont l'identifiant est dans `FACEBOOK_CLIENT_ID`. L'explorateur émet
   sinon un token rattaché à une autre app, et l'échange est refusé avec
   `The access token does not belong to application <id>`.
2. Demander les permissions `pages_show_list`, `pages_read_engagement` et
   `pages_manage_posts`.

En cas de doute sur l'app à laquelle un token appartient, le
[débogueur de token](https://developers.facebook.com/tools/debug/accesstoken/)
affiche son App ID.

Le token se passe ensuite **en paramètre de requête** — il est obligatoire :

```bash
curl -X POST "http://127.0.0.1:8080/admin/facebook/bootstrap?shortLivedToken=LE_TOKEN"
```

Réponse attendue : `Token successfully initialized`. Le service échange ce token
contre un token utilisateur longue durée, en dérive le token de page, et stocke
les deux en base (table `facebook_tokens`). Vérifier ensuite :

```bash
curl -s http://127.0.0.1:8080/admin/facebook/token/status
```

Un `expiresAt` à dix ans (`daysRemaining` ≈ 3650) est normal : Facebook a renvoyé
un token permanent (`expires_at: 0`), que le code convertit en `now + 10 ans`.

En cas d'échec, l'erreur de Facebook est renvoyée telle quelle dans la réponse
HTTP (message et code), sans avoir à consulter les logs du conteneur.

Le renouvellement ultérieur est automatique (`FacebookTokenScheduler`, cron
`0 0 3 * * *`). C'est la raison de la variable `TZ` : sans elle, le conteneur
serait en UTC et le renouvellement aurait lieu à une autre heure locale.

## Déploiement automatique

Depuis [`deploy-preprod.yml`](../.github/workflows/deploy-preprod.yml), toute
poussée sur `develop` touchant `apps/**` ou le compose de préproduction déploie
la préproduction. Le workflow est aussi lançable à la main
(*Actions → Deploy preprod → Run workflow*) pour redéployer sans nouveau commit.

Déroulé :

1. **Build & push** des deux images sur GHCR, en parallèle, taguées au SHA du
   commit **et** en `latest`. Le cache de build est celui d'Actions : une
   modification du seul front ne reconstruit pas les couches Maven.
2. **Régénération du `.env`** sur le VPS depuis l'environnement GitHub, avec
   `IMAGE_TAG` = SHA du commit. L'ancien fichier est sauvegardé en `.env.bak`.
3. **`docker compose pull` puis `up -d --no-build --remove-orphans`.**
   `--no-build` est délibéré : si une image manquait, on veut un échec net
   plutôt qu'un build Maven + Angular improvisé sur un VPS à 1 vCPU.
4. **Vérification** : `/actuator/health` sur la boucle locale (jusqu'à 3 min, le
   temps du `start_period`), puis `https://$PREPROD_HOST` **depuis le VPS**, en
   sortant par `--interface 10.8.0.1`. Ce détour est nécessaire : le runner
   GitHub est hors du réseau WireGuard et recevrait 403
   (cf. [Accès restreint au VPN](#accès-restreint-au-vpn)). Les 404 transitoires
   qui suivent un `up -d` sont absorbés par les tentatives successives.

### Configuration attendue

Le format du `.env` n'a pas changé ; seule son origine a évolué. GitHub en est
désormais la source de vérité, via un Environment `preprod`
(*Settings → Environments → preprod*) :

| Type GitHub | Clés |
|---|---|
| **Variables** (non sensibles, lisibles) | `PREPROD_HOST`, `PREPROD_ALLOWED_CIDRS`, `POSTGRES_DB`, `POSTGRES_USER`, `FACEBOOK_API_VERSION`, `FACEBOOK_CLIENT_ID`, `FACEBOOK_PAGE_ID`, `TZ` |
| **Variables** (accès au VPS) | `PREPROD_SSH_HOST`, `PREPROD_SSH_USER`, `PREPROD_SSH_KNOWN_HOSTS`, `PREPROD_PATH` |
| **Secrets** | `POSTGRES_PASSWORD`, `FACEBOOK_CLIENT_SECRET`, `SSH_PRIVATE_KEY` |

`PREPROD_SSH_USER` (défaut `agorapilot`), `PREPROD_PATH` (défaut
`/opt/agorapilot/preprod/repo`), `PREPROD_ALLOWED_CIDRS`, `FACEBOOK_API_VERSION`
et `TZ` ont une valeur de repli dans le workflow ; les autres sont obligatoires
et leur absence fait échouer le déploiement avec un message nommant la clé.

`PREPROD_SSH_KNOWN_HOSTS` contient l'empreinte du serveur, obtenue par :

```bash
ssh-keyscan -H <ip-du-vps>
```

Elle est épinglée plutôt que découverte à la volée : sans elle, n'importe quelle
machine répondant à cette adresse recevrait la clé de déploiement.

> ⚠️ **`POSTGRES_PASSWORD` doit reprendre le mot de passe déjà en place.**
> PostgreSQL conserve celui fixé à l'initialisation du volume `db-data` : une
> valeur différente ne le change pas, elle fait seulement échouer la connexion
> du back. Le relire sur le VPS avant de créer le secret :
> `grep POSTGRES_PASSWORD /opt/agorapilot/preprod/repo/.env`.

### Authentification GHCR du VPS

Aucune. Le jeton du workflow (`GITHUB_TOKEN`) est transmis au VPS le temps du
`docker login`, puis le workflow fait `docker logout` — le jeton expire de toute
façon à la fin du run. Le VPS ne stocke donc aucun identifiant GHCR durable, et
les images peuvent rester privées.

### Première exécution

Le VPS est aujourd'hui un clone du dépôt ; le workflow y dépose simplement le
compose et le `.env` à jour, sans y toucher autrement. Le clone peut être réduit
plus tard à ces deux fichiers, mais le garder ne coûte rien : `--no-build`
interdit d'utiliser les sources qui s'y trouvent.

En cas d'échec du premier déploiement, le retour arrière est celui du runbook
manuel : restaurer `.env.bak`, et redéployer un tag antérieur en fixant
`IMAGE_TAG` à la main dans le `.env` avant `docker compose up -d`.

## Dette connue

- **`ddl-auto: update`** : le schéma est encore dérivé des entités JPA. Le
  passage à Flyway est un prérequis avant la mise en production
  (cf. [`gitflow.md`](./gitflow.md)).
- **Aucune authentification applicative** : la chaîne de filtres du profil
  `preprod` est permissive, comme en développement. La préproduction ne doit
  donc pas contenir de données réelles sensibles.
- **Swagger UI est public** sur la préproduction (`/swagger-ui/`), choix assumé
  pour un environnement de test.
- **La validité réelle du token Facebook n'est jamais vérifiée.**
  `FacebookTokenService.renewIfNeeded()` ne se déclenche que si la date
  d'expiration *stockée* arrive à moins de 15 jours. Avec un token permanent
  (enregistré à dix ans), le job nocturne ne fait donc jamais rien — ce qui est
  correct tant que le token reste valide. Mais si Facebook l'invalide
  prématurément (mot de passe changé, permission révoquée, revue d'app), rien ne
  le détecte : on ne l'apprend qu'au premier échec de publication. Un appel
  périodique à `debug_token` pour contrôler `is_valid` comblerait ce trou.
