# Déploiement de la préproduction (VPS)

Runbook du déploiement manuel de la préproduction. Il décrit l'état actuel :
le déploiement automatique par la CI GitHub est l'étape suivante et réutilisera
exactement ce montage (cf. [Bascule vers la CI/CD](#bascule-vers-la-cicd)).

## Vue d'ensemble

Trois conteneurs, décrits par [`docker-compose.preprod.yaml`](../docker-compose.preprod.yaml) :

| Service | Rôle | Exposition |
|---|---|---|
| `front` | nginx : sert la SPA Angular **et** proxifie `/api` vers le back | Réseau `web` → Traefik → `https://$PREPROD_HOST` |
| `back` | Spring Boot, profil `preprod` | Réseau interne + `127.0.0.1:8080` sur le VPS |
| `db` | PostgreSQL 16, volume `db-data` | Réseau interne uniquement |

Deux conséquences de ce montage :

- **Une seule origine.** Le front appelle `/api` en relatif, nginx relaie vers le
  back. Aucun CORS à configurer, et la même image tourne en preprod comme en
  prod sans reconstruction.
- **Un seul point d'entrée public.** Seul `front` est attaché au réseau Traefik.
  La base n'est joignable par personne d'autre que le back.

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

```bash
cd /opt/agorapilot/preprod/repo && git pull && docker compose -f docker-compose.preprod.yaml up -d --build
```

Une fois les images publiées sur GHCR, `IMAGE_TAG` renseigné dans le `.env` :

```bash
docker compose -f docker-compose.preprod.yaml pull && docker compose -f docker-compose.preprod.yaml up -d
```

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

## Bascule vers la CI/CD

Le format du `.env` ne changera pas ; seule son origine évoluera. GitHub
deviendra la source de vérité, via un Environment `preprod` :

| Type GitHub | Variables |
|---|---|
| **Variables** (non sensibles, lisibles) | `PREPROD_HOST`, `POSTGRES_DB`, `POSTGRES_USER`, `FACEBOOK_PAGE_ID`, `FACEBOOK_API_VERSION`, `TZ` |
| **Secrets** | `POSTGRES_PASSWORD`, `FACEBOOK_CLIENT_SECRET`, `SSH_PRIVATE_KEY` |

Le workflow de déploiement, déclenché à chaque merge sur `develop`, régénérera le
`.env` sur le VPS depuis ces valeurs avec `IMAGE_TAG` = SHA du commit, puis
`docker compose pull && up -d`. Restent à créer : le workflow de publication sur
GHCR (avec `permissions: packages: write`) et celui de déploiement.

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
