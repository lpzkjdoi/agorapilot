# Préproduction publique : fin du filtre VPN, durcissement et limites mémoire

**Date** : 2026-11-18
**Branche** : `claude/preprod-publique`

## Contexte

Chantier 1 de la mise en production. Le filtre WireGuard de la préprod datait de
juillet, quand elle n'avait aucune authentification. Les comptes existent depuis
octobre : la préprod peut s'ouvrir, et sert ainsi de répétition générale d'une
production publique. Il fallait d'abord ce qui protège une application exposée :
une limite d'échecs de connexion par IP et des en-têtes de sécurité.

Le passage sur `preprod.agorapilot.fr` attend l'achat du domaine : la procédure
est écrite, aucun code n'en dépend (tout passe par `PREPROD_HOST`).

## Changements

### Back

- `LoginRateLimiter` : au-delà de **20 échecs de connexion en 15 minutes depuis
  une même IP**, tous pseudos confondus, `POST /api/auth/login` répond **429**
  (« Trop de tentatives de connexion… ») jusqu'à la fin de la fenêtre, sans
  vérifier le mot de passe. Seuls les échecs comptent. En mémoire, réglable par
  `LOGIN_RATE_LIMIT_MAX_FAILURES` / `LOGIN_RATE_LIMIT_WINDOW`. Complète le
  verrouillage par compte (5 échecs), qui n'arrête pas l'essai d'un mot de passe
  sur beaucoup de pseudos.
- `server.forward-headers-strategy: native` dans les profils `preprod` et `prod` :
  l'IP du client est lue dans `X-Forwarded-For`, en ne faisant confiance qu'aux
  proxies des réseaux privés (Traefik, nginx). Sans cela, tout le monde aurait
  l'IP de nginx et la limite bloquerait tous les utilisateurs à la fois.

### Front (nginx)

- `docker/headers/security.conf`, inclus par les deux confs : CSP
  (`script-src 'self'`, `style-src 'self' 'unsafe-inline'` pour les styles des
  composants Angular, `frame-ancestors 'none'`…), HSTS un an, `nosniff`,
  `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`.
- `docker/headers/noindex.conf` : `X-Robots-Tag: noindex, nofollow`, préprod
  seulement.
- Les deux fichiers sont inclus dans `server` **et** dans les `location` qui
  posent leurs propres en-têtes (cache) : nginx n'hérite pas des `add_header`
  d'un niveau qui en déclare.
- `angular.json` : **CSS critique en ligne désactivé** (`inlineCritical: false`).
  Le build de production insérait `onload="this.media='all'"` sur la feuille de
  style ; la CSP bloque ce gestionnaire en ligne, et l'application se serait
  affichée sans ses styles.

### Préprod

- Middleware Traefik `ipwhitelist` et variable `PREPROD_ALLOWED_CIDRS` retirés
  (compose, workflow, `.env.preprod.example`).
- Limites mémoire, identiques à la prod : back 768 Mo, base 256 Mo, front 64 Mo.
- Le déploiement vérifie la préprod depuis le runner GitHub (et non plus par
  `--interface 10.8.0.1` depuis le VPS), y compris la présence des en-têtes
  `Content-Security-Policy` et `X-Robots-Tag`. Le workflow de prod vérifie la CSP
  et l'**absence** de `X-Robots-Tag`.

### Docs

Runbook préprod (section « Accès public », procédure de bascule de domaine),
runbook prod, `configuration-meta.md`, README du front, roadmap, commentaires de
`FacebookClient` (la raison d'envoyer les binaires reste valable : les médias
exigent une session).

## Vérification effectuée

- `mvn -B verify` : 710 cas de test, 0 échec, dont 4 nouveaux sur la limite par
  IP (refus en 429 même avec le bon mot de passe, autres IP non touchées, levée
  en fin de fenêtre, connexions réussies non comptées).
- `npm test` : 715 tests verts, dont le nouveau cas de la page de connexion, qui
  affiche le message du back sur un 429 (au lieu du message générique) ; `ng lint` propre.
- `nginx -t` sur les deux confs (`nginx:1.27-alpine`, sur le VPS, dans un
  dossier temporaire supprimé ensuite) : syntaxe valide.
- `docker compose config` du compose préprod sur le VPS : limites mémoire
  présentes, plus aucun middleware de filtrage.
- **CSP dans le navigateur** : build de production servi en local avec les
  en-têtes exacts de `security.conf` et `noindex.conf`, API bouchonnée. Les
  pages s'affichent avec leurs styles, aucune violation CSP en console. C'est ce
  test qui a révélé le problème du CSS critique.
- **Non vérifié** : la lecture de `X-Forwarded-For` derrière Traefik et nginx
  (MockMvc ne passe pas par la valve Tomcat). À contrôler en préprod après
  déploiement : 21 échecs depuis une IP doivent donner 429 à cette IP seulement.

## Reste à faire

- Supprimer `PREPROD_ALLOWED_CIDRS` de l'environnement GitHub `preprod`.
- WireGuard sur le VPS (root), s'il ne sert plus à rien d'autre.
- Bascule sur `preprod.agorapilot.fr` une fois le domaine acheté.
- Scan Trivy bloquant.

## Commit

_voir PR_
