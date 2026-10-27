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

Attendu : **401**. L'API est fermée à qui n'est pas connecté (cas verrouillé par
`SecurityConfigPreprodTest`). Un **200** signifierait qu'elle est ouverte.

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

## Migrations de schéma (Flyway)

Le schéma de la base est porté par les migrations Flyway de
[`apps/back/src/main/resources/db/migration`](../apps/back/src/main/resources/db/migration),
jouées au démarrage du back. Hibernate ne modifie plus rien (`ddl-auto: validate`) :
il vérifie seulement que le schéma correspond aux entités, et le back refuse de
démarrer sinon.

Faire évoluer le schéma, c'est ajouter un fichier `V<n>__<sujet>.sql` (SQL commun
à PostgreSQL et à H2, que les tests jouent aussi). On ne modifie **jamais** une
migration déjà jouée : Flyway compare leurs sommes de contrôle et refuserait de
démarrer. Une nouvelle valeur d'enum, par exemple, est une migration qui
remplace la contrainte `CHECK` de la colonne. Elle n'est plus un `ALTER TABLE`
à passer à la main.

### Premier déploiement avec Flyway

La base de préproduction a été créée par Hibernate, avant Flyway. Au premier
démarrage, Flyway constate qu'elle a des tables mais pas d'historique : il
l'inscrit d'office en version 1 (`baseline-on-migrate`) et **ne rejoue pas** `V1`.
Rien n'est à faire avant le déploiement : les données de préproduction sont
jetables.

Si le back refuse de démarrer (`validate` a trouvé un écart entre la base et les
entités), ou si une migration future bute sur une contrainte au nom différent,
il suffit de repartir d'une base vide, que Flyway reconstruit à partir de `V1` :

```bash
docker compose -f docker-compose.preprod.yaml down
docker volume rm "$(docker volume ls -q | grep 'db-data$')"
docker compose -f docker-compose.preprod.yaml up -d
```

Après le déploiement, `flyway_schema_history` montre la version de la base :

```bash
docker compose -f docker-compose.preprod.yaml exec -T db \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT version, description, type, success FROM flyway_schema_history"
```

`1 | Schéma créé par Hibernate avant Flyway | BASELINE` sur l'ancienne base,
`1 | schema initial | SQL` sur une base reconstruite.

Les deux migrations manuelles de septembre (statut `FAILED` des occurrences,
statut `IN_PROGRESS` des livraisons) sont déjà passées et intégrées à `V1`, qui
porte les contraintes à jour. Elles restent décrites dans leurs changelogs
respectifs.

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

## Comptes et connexion

L'application exige un compte (cf.
[proposition](./proposals/2026-10-24-comptes-et-droits.md)). Tout est refusé par
défaut : un compte n'a que les droits qu'on lui donne.

### Premier compte

Au démarrage, si la base n'a **aucun** compte, le back crée un administrateur
(tous les droits) à partir de deux valeurs de l'Environment GitHub `preprod` :

- Variable `AGORAPILOT_ADMIN_USERNAME` : 3 à 32 caractères parmi `a-z`, `0-9`,
  `.`, `_` et `-` ;
- Secret `AGORAPILOT_ADMIN_PASSWORD` : 12 caractères au moins.

Ce mot de passe est **provisoire** : à la première connexion, l'application
demande d'en choisir un autre avant toute chose. Une valeur hors règle empêche
le back de démarrer, plutôt que de créer un compte faible. Sans ces valeurs,
une base vide démarre sans compte, et personne ne peut se connecter.

Une fois le compte créé, ces valeurs ne servent plus. Les retirer de
l'Environment n'a aucun effet sur le compte.

### Administrer les comptes

Les autres comptes se créent depuis la page **Comptes** de l'application, qui
n'apparaît qu'aux comptes ayant le droit `users:manage`. On y crée un compte, on
règle ses droits à partir d'un profil (Lecteur, Rédacteur, Diffuseur,
Administrateur), on le désactive ou on réinitialise son mot de passe. Le mot de
passe provisoire ne s'affiche qu'une seule fois : il faut le transmettre à la
personne par un canal sûr. Réactiver un compte désactivé passe par une
réinitialisation : il repart avec un nouveau mot de passe provisoire.

Deux garde-fous s'ajoutent au droit `users:manage` :

- **aucune action sur son propre compte** (ses droits, sa désactivation, son mot
  de passe, qui se change depuis le lien du pseudo dans le bandeau). Il reste
  donc toujours au moins un administrateur ;
- **on ne donne ni ne retire que des droits qu'on détient soi-même**.

Le **journal de sécurité**, en bas de la même page, liste les connexions, les
refus, les verrouillages et toutes les actions d'administration. Il garde aussi
la trace des actions qui ne laissent pas de ligne ailleurs : diffusions
déplacées, reprises ou annulées, publications supprimées, canaux suspendus
(par l'ordonnanceur) et repris. Les cartes de publication indiquent leur auteur
et leur dernier modificateur, et la bulle du calendrier qui a programmé chaque
diffusion.

### Sessions

- Le cookie de session est `HttpOnly`, `Secure` et `SameSite=Strict`. Il
  expire après 30 minutes d'inactivité, et au plus tard 12 heures après la
  connexion.
- Après 5 échecs de connexion consécutifs, le compte est verrouillé pendant
  15 minutes.
- Statut et droits sont relus à chaque requête. Désactiver un compte ou lui
  retirer un droit prend effet tout de suite, et changer de mot de passe coupe
  ses autres sessions.

### Appeler l'API depuis le VPS

Les endpoints `/admin/**` (jeton Facebook, état de l'ordonnanceur) exigent une
session ouverte par un compte qui a le droit `channels:manage`, ainsi que le
jeton CSRF sur les requêtes qui modifient. Depuis le VPS, ou à travers un tunnel
SSH (`ssh -L 8080:127.0.0.1:8080 utilisateur@vps`) :

```bash
B=http://127.0.0.1:8080
read -rp 'Pseudo : ' U; read -rsp 'Mot de passe : ' P; echo
XSRF=$(curl -s -D - -o /dev/null $B/api/me | sed -n 's/^[Ss]et-[Cc]ookie: XSRF-TOKEN=\([^;]*\).*/\1/p' | tr -d '\r')
SESSION=$(curl -s -D - -o /dev/null -H "Cookie: XSRF-TOKEN=$XSRF" -H "X-XSRF-TOKEN: $XSRF" \
  -H 'Content-Type: application/json' -d "{\"username\":\"$U\",\"password\":\"$P\"}" \
  $B/api/auth/login | sed -n 's/^[Ss]et-[Cc]ookie: JSESSIONID=\([^;]*\).*/\1/p' | tr -d '\r')
unset P
C="Cookie: JSESSIONID=$SESSION; XSRF-TOKEN=$XSRF"
```

Les cookies passent en en-tête plutôt que par `curl -c` / `-b` : le cookie de
session est `Secure`, et `curl` refuse de le renvoyer en HTTP. Un mot de passe
contenant `"` ou `\` casse ce JSON : le changer, ou écrire le corps à la main.

Ensuite, pour une lecture : `curl -s -H "$C" $B/admin/scheduler/status`. Pour
une écriture, ajouter le jeton : `curl -s -X POST -H "$C" -H "X-XSRF-TOKEN: $XSRF" …`.
Pour fermer la session :

```bash
curl -s -X POST -H "$C" -H "X-XSRF-TOKEN: $XSRF" $B/api/auth/logout
```

## Connexion du canal Facebook (depuis l'interface)

La page **« Canal Facebook »** (`/canal-facebook`, droit `channels:manage`)
remplace le bootstrap à la main décrit plus bas. Elle passe par le flux OAuth de
Facebook Login : le bouton « Connecter la Page Facebook » envoie le navigateur
sur la boîte de dialogue de Facebook, qui le ramène sur AgoraPilot avec un code
que le back échange contre le jeton de la Page `FACEBOOK_PAGE_ID`.

### Prérequis côté Meta (une fois par environnement)

Dans l'app Meta (`FACEBOOK_CLIENT_ID`) → Facebook Login → Paramètres, ajouter aux
**« Valid OAuth Redirect URIs »** :

```
https://<PREPROD_HOST>/api/channels/facebook/callback
```

Sans cela, Facebook refuse la connexion (« URL bloquée »). Le VPN ne gêne pas :
la redirection passe par le navigateur, jamais par les serveurs de Facebook.

La personne qui connecte doit administrer la Page et, tant que l'app n'a pas
d'accès avancé, avoir un rôle dans l'app Meta.

### Ce que fait la connexion

- jeton de Page permanent stocké dans `facebook_connections` (une ligne, remplacée
  à chaque reconnexion) ; jamais renvoyé au front ;
- suspension Facebook levée automatiquement ;
- entrée « Page Facebook connectée » dans le journal de sécurité, au nom de la
  personne qui a cliqué.

Le retour de Facebook (`GET /api/channels/facebook/callback`) est **public** : le
cookie de session (`SameSite=strict`) n'accompagne pas une navigation venue de
facebook.com. C'est le `state` OAuth, à usage unique, valable 10 minutes et
stocké dans `facebook_oauth_states`, qui identifie la demande et son auteur.

### Contrôle quotidien

Chaque matin à 7 h (`FACEBOOK_CONNECTION_CHECK_CRON`, défaut `0 0 7 * * *`), le
back interroge `debug_token`. Un jeton invalide, ou sans `pages_manage_posts`,
**suspend le canal** avant la fenêtre de publication, avec un bandeau qui renvoie
vers « Canal Facebook ». Facebook injoignable ne suspend rien (résultat
« Indéterminé »). Le bouton « Contrôler maintenant » fait la même chose à la
demande.

### Bascule et retour arrière

L'ancien mécanisme est intact ; la variable `FACEBOOK_TOKEN_SOURCE` choisit :

| Valeur | Jeton qui publie | Connexion / contrôle |
|---|---|---|
| `connection` (défaut) | le jeton connecté ; **à défaut**, le jeton historique de `facebook_tokens` | actifs |
| `legacy` | le jeton historique seul, comme avant | désactivés (la page l'indique) |

La bascule ne coupe donc rien : tant que personne n'a connecté la Page, le jeton
posé par le bootstrap continue de publier.

**Revenir en arrière** sans toucher au code : définir la variable
`FACEBOOK_TOKEN_SOURCE=legacy` dans l'Environment GitHub `preprod`, puis relancer
« Deploy preprod » (`workflow_dispatch`). Les tables `facebook_connections` et
`facebook_oauth_states` restent en place, ignorées.

**Annuler la PR elle-même** (revert) reste possible : Flyway ignore une migration
appliquée plus récente que le code (`V5`), et Hibernate des tables qu'il ne
connaît pas. Seule précaution : supprimer d'abord les lignes du journal de type
`FACEBOOK_CONNECTED`, que l'ancien code ne saurait pas lire (la page « Comptes »
échouerait) :

```sql
DELETE FROM security_events WHERE type = 'FACEBOOK_CONNECTED';
```

## Endpoints d'administration Facebook

> Mécanisme historique, conservé le temps de valider la connexion depuis
> l'interface (cf. section précédente) ; il sert encore quand
> `FACEBOOK_TOKEN_SOURCE=legacy` ou tant qu'aucune Page n'a été connectée.


`FacebookAdminController` expose `POST /admin/facebook/bootstrap` et
`GET /admin/facebook/token/status`, réservés aux comptes qui ont le droit
`channels:manage`. Ils ne sont **pas** proxifiés par le nginx du front : depuis
Internet, `/admin/...` renvoie l'`index.html` de la SPA, jamais l'API.

Ils restent joignables sur la boucle locale du VPS, avec une session ouverte
comme décrit dans [Appeler l'API depuis le VPS](#appeler-lapi-depuis-le-vps).

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
curl -s -X POST -H "$C" -H "X-XSRF-TOKEN: $XSRF" "$B/admin/facebook/bootstrap?shortLivedToken=LE_TOKEN"
```

Réponse attendue : `Token successfully initialized`. Le service échange ce token
contre un token utilisateur longue durée, en dérive le token de page, et stocke
les deux en base (table `facebook_tokens`). Vérifier ensuite :

```bash
curl -s -H "$C" $B/admin/facebook/token/status
```

Un `expiresAt` à dix ans (`daysRemaining` ≈ 3650) est normal : Facebook a renvoyé
un token permanent (`expires_at: 0`), que le code convertit en `now + 10 ans`.

En cas d'échec, l'erreur de Facebook est renvoyée telle quelle dans la réponse
HTTP (message et code), sans avoir à consulter les logs du conteneur.

Le renouvellement ultérieur est automatique (`FacebookTokenScheduler`, cron
`0 0 3 * * *`). C'est la raison de la variable `TZ` : sans elle, le conteneur
serait en UTC et le renouvellement aurait lieu à une autre heure locale.

## État de l'ordonnanceur de diffusion

`GET /admin/scheduler/status`, comme les endpoints Facebook : droit
`channels:manage`, non proxifié par le nginx du front, joignable seulement
depuis le VPS.

```bash
curl -s -H "$C" $B/admin/scheduler/status
```

| Champ | Sens |
|---|---|
| `lastRunAt` | dernier balayage ; doit avoir moins de `occurrencesDelay` |
| `lastErrorAt` / `lastError` | dernière erreur inattendue du balayage, conservée jusqu'à la suivante |
| `occurrencesDelay` | période de balayage (`OCCURRENCES_SCHEDULER_DELAY`, défaut `PT1M`) |
| `maxLateness` | retard au-delà duquel une livraison n'est plus diffusée (`OCCURRENCES_MAX_LATENESS`, défaut `PT1H`) |
| `windowStart` / `windowEnd` | fenêtre de publication Facebook où sont réparties les diffusions programmées pour un jour (`PUBLICATION_WINDOW_START` / `PUBLICATION_WINDOW_END`, défaut `15:00` / `22:00`) |

Un refus de Facebook n'apparaît **pas** dans `lastError` : il est tracé sur la
livraison elle-même (`FAILED` + `errorMessage`). `lastError` ne capte que les
pannes du balayage (base injoignable, bug).

Une livraison peut finir `FAILED` sans avoir été tentée, avec l'un de ces motifs :

- `Not published: more than 60 minutes late` : le back était arrêté à l'heure
  prévue, et l'annonce est jugée périmée ;
- `Delivery interrupted before its outcome was recorded` : le back s'est arrêté
  pendant l'appel à Facebook. Le post **a pu partir** : vérifier la page avant
  de relancer.

Un refus de Facebook est tracé avec son seul message, suivi de ses codes :
`Confirmez votre identité… (Facebook, code 368, sous-code 4854002)`. La réponse
complète, avec le `fbtrace_id` à fournir au support de Meta, est dans les logs :

```bash
docker compose -f docker-compose.preprod.yaml logs back | grep "Facebook a répondu"
```

Refus rencontrés :

| Codes | Cause | Remède |
|---|---|---|
| 368 / 4854002 | Meta exige que l'administrateur de la Page confirme son identité | Ouvrir l'application Facebook sur le téléphone de l'administrateur (ou facebook.com/id/hub) et suivre les instructions. Les autres administrateurs de la Page peuvent devoir le faire aussi |
| 190 | Jeton invalide ou expiré | Refaire le bootstrap (`POST /admin/facebook/bootstrap`) |

Une livraison `FAILED` n'est jamais retentée automatiquement. Une fois la cause
réglée, la reprendre depuis le calendrier (bouton **« Reprendre »** dans la
bulle du jour, ou `POST /api/occurrences/{id}/retry` avec `{"date": …}`).

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
`/opt/agorapilot/preprod/repo`), `PREPROD_ALLOWED_CIDRS`, `FACEBOOK_API_VERSION`,
`FACEBOOK_TOKEN_SOURCE` (défaut `connection`, cf.
[Bascule et retour arrière](#bascule-et-retour-arrière)) et `TZ` ont une valeur de repli dans le workflow ; les autres sont obligatoires
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

- **La validité réelle du token Facebook n'est jamais vérifiée.**
  `FacebookTokenService.renewIfNeeded()` ne se déclenche que si la date
  d'expiration *stockée* arrive à moins de 15 jours. Avec un token permanent
  (enregistré à dix ans), le job nocturne ne fait donc jamais rien — ce qui est
  correct tant que le token reste valide. Mais si Facebook l'invalide
  prématurément (mot de passe changé, permission révoquée, revue d'app), rien ne
  le détecte : on ne l'apprend qu'au premier échec de publication. Un appel
  périodique à `debug_token` pour contrôler `is_valid` comblerait ce trou.
  **Comblé pour le jeton connecté depuis l'interface** (contrôle quotidien, cf.
  [Connexion du canal Facebook](#connexion-du-canal-facebook-depuis-linterface)) ;
  reste vrai du jeton historique, à supprimer avec le bootstrap.
