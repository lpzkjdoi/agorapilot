# Déploiement de la production

Runbook de la production AgoraPilot. Le pendant pour la préproduction est
[`deploiement-preprod.md`](./deploiement-preprod.md) ; ce document ne reprend que
ce qui diffère.

## Vue d'ensemble

```
develop ──(PR)──► main ──(tag vX.Y.Z)──► deploy-prod.yml
                                          ├─ check   : version X.Y.Z, tag contenu dans main
                                          ├─ build   : images prod-X.Y.Z (badge « X.Y.Z », nginx sans Swagger)
                                          ├─ scan    : Trivy, non bloquant
                                          └─ deploy  : ⏸ approbation « production », puis SSH sur le VPS
```

- **Machine** : le VPS de la préprod (`srv753950`), dans `/opt/agorapilot/prod`.
  Les deux stacks partagent le Traefik et le démon Docker, et rien d'autre : nom
  de projet, conteneurs, réseau interne, volumes, routeurs et port local sont
  distincts.
- **Domaine** : `agorapilot.fr`, et `www.agorapilot.fr` redirigé vers lui.
- **Images** : `ghcr.io/lpzkjdoi/agorapilot-{back,front}:prod-X.Y.Z`. Jamais de
  `latest` en production. La préprod a ses propres tags (`preprod-<sha>`).
- **Mémoire** : limites posées par le compose (back 768 Mo, base 256 Mo, front
  64 Mo), le VPS n'ayant que 3,9 Go sans swap.

| | Préproduction | Production |
|---|---|---|
| Déclencheur | fusion sur `develop` | tag `vX.Y.Z` sur `main` |
| Approbation | aucune | environnement GitHub `production` |
| Compose | `docker-compose.preprod.yaml` | `docker-compose.prod.yaml` |
| Dossier VPS | `/opt/agorapilot/preprod/repo` | `/opt/agorapilot/prod` |
| Profil Spring | `preprod` | `prod` |
| Back sur la boucle locale | `127.0.0.1:8080` | `127.0.0.1:8081` |
| Accès | public (`X-Robots-Tag: noindex`) | public |
| Swagger proxifié par nginx | oui | non (`nginx.prod.conf`) |
| App Meta | app de préprod, mode développement | app de production, mode Live |

## Mise en place (une seule fois)

### 1. DNS

Enregistrements **A** vers `46.202.175.115` :

- `agorapilot.fr`
- `www.agorapilot.fr`

Pas d'AAAA pour l'instant. Le certificat est obtenu par Traefik (TLS-ALPN) au
premier accès après le déploiement. `www` a son propre routeur et son propre
certificat : s'il manque au DNS, seul `www` est en erreur.

### 2. Dossier sur le VPS

Le compte `agorapilot` possède `/opt/agorapilot` : le workflow crée lui-même
`/opt/agorapilot/prod` au premier déploiement. Rien à faire en root.

### 3. App Meta de production

Une app distincte de celle de la préprod (cf.
[`configuration-meta.md`](./configuration-meta.md)), qui déclare :

- domaine de l'app : `agorapilot.fr` ;
- plateforme Site web : `https://agorapilot.fr/` ;
- URI de redirection OAuth : `https://agorapilot.fr/api/channels/facebook/callback`.

### 4. Environnement GitHub `production`

Settings → Environments → **New environment** → `production` :

- **Required reviewers** : Maxime ;
- **Deployment branches and tags** : règles `v*` (tag) et `main` (branche, pour
  les relances manuelles) ;
- **Variables** :

| Variable | Valeur |
|---|---|
| `PROD_HOST` | `agorapilot.fr` |
| `PROD_SSH_HOST` | `46.202.175.115` |
| `PROD_SSH_USER` | `agorapilot` (valeur par défaut, facultative) |
| `PROD_SSH_KNOWN_HOSTS` | sortie de `ssh-keyscan -H 46.202.175.115` (la même qu'en préprod) |
| `PROD_PATH` | `/opt/agorapilot/prod` (valeur par défaut, facultative) |
| `POSTGRES_DB`, `POSTGRES_USER` | `agorapilot` |
| `FACEBOOK_API_VERSION` | `v25.0` (facultative) |
| `FACEBOOK_CLIENT_ID`, `FACEBOOK_PAGE_ID` | app Meta et Page **de production** |
| `TZ` | `Europe/Paris` (facultative) |
| `AGORAPILOT_ADMIN_USERNAME` | premier compte administrateur |

- **Secrets** :

| Secret | Valeur |
|---|---|
| `SSH_PRIVATE_KEY` | clé de déploiement (la même qu'en préprod, ou une clé dédiée ajoutée à `authorized_keys`) |
| `POSTGRES_PASSWORD` | `openssl rand -hex 32`, **figé** dès le premier démarrage |
| `FACEBOOK_CLIENT_SECRET` | secret de l'app Meta de production |
| `AGORAPILOT_ADMIN_PASSWORD` | mot de passe provisoire du premier compte (12 caractères au moins) |
| `NOTIFICATIONS_DISCORD_*_URL` | facultatifs : salons Discord de la production |

Le contrat complet est dans [`.env.prod.example`](../.env.prod.example).

## Publier une version

1. Fusionner `develop` dans `main` par une PR (préprod validée).
2. Taguer le commit de fusion sur `main`, puis pousser le tag :

   ```bash
   git fetch origin && git tag -a v1.0.0 origin/main -m "v1.0.0" && git push origin v1.0.0
   ```

3. Dans l'onglet Actions, le run « Deploy prod » construit et scanne les images,
   puis attend : **Review deployments → Approve**.
4. Le run vérifie que le back répond `UP` sur `127.0.0.1:8081`, puis que
   `https://agorapilot.fr` répond 200 depuis Internet.

Un tag qui n'est pas sur `main`, ou qui n'est pas de la forme `vX.Y.Z` (une
pré-version `-rc.1` par exemple), est refusé dès le job `check`.

## Revenir à une version précédente

Actions → Deploy prod → **Run workflow** (branche `main`), en saisissant la
version visée, par exemple `1.0.0`. Les images `prod-1.0.0` existant déjà, elles
sont redéployées telles quelles, sans rebuild, après approbation.

⚠️ **Les migrations Flyway ne reviennent pas en arrière.** Une version plus
ancienne démarre sur le schéma de la plus récente. Hibernate en `validate`
tolère les colonnes et tables ajoutées, mais une colonne supprimée ou renommée
fera échouer le démarrage. Avant de publier une version qui contient une
migration destructive, s'assurer qu'aucun retour arrière ne sera nécessaire,
ou restaurer la base (cf. sauvegardes, à mettre en place).

En dépannage depuis le VPS, le `.env.bak` laissé par chaque déploiement contient
l'`IMAGE_TAG` de la version précédente :

```bash
cd /opt/agorapilot/prod && cp .env.bak .env && docker compose -f docker-compose.prod.yaml up -d --no-build
```

## Exploitation

```bash
ssh -i ~/.ssh/agorapilot-deploy agorapilot@46.202.175.115
cd /opt/agorapilot/prod
docker compose -f docker-compose.prod.yaml ps
docker compose -f docker-compose.prod.yaml logs --tail=100 back
curl -s http://127.0.0.1:8081/actuator/health
```

Accès psql sans afficher de secret :

```bash
docker compose -f docker-compose.prod.yaml exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## Avant d'ouvrir la production

Hors du périmètre de ce workflow, et suivis dans la roadmap du README :

- sauvegardes de `db-data` et `media-data`, hors du VPS, avec une restauration testée ;
- fin du durcissement : scan Trivy bloquant (la limite d'échecs de connexion
  par IP et les en-têtes de sécurité sont en place depuis le 2026-11-18) ;
- images de base épinglées par digest ;
- sonde de disponibilité externe ;
- côté VPS (root) : redémarrage en attente, `PermitRootLogin`, swap.
