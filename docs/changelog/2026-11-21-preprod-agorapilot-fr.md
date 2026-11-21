# 2026-11-21 — Préprod déplacée sur `preprod.agorapilot.fr`

## Contexte

Le domaine `agorapilot.fr` a été acheté chez Hostinger le 2026-11-21. La préprod
quitte `preprod.chariotte-manager.fr` pour `preprod.agorapilot.fr`, en suivant la
procédure de bascule préparée dans
[`deploiement-preprod.md`](../deploiement-preprod.md#bascule-vers-preprodagorapilotfr).
La prod sur `agorapilot.fr` n'est pas concernée ici.

## Changements

Hors dépôt :

- **DNS (Maxime)** : enregistrements A `agorapilot.fr`, `www.agorapilot.fr` et
  `preprod.agorapilot.fr` vers `46.202.175.115`, TTL 300, sans AAAA.
- **App Meta de préprod (Maxime)** : domaine, plateforme Site web et URI de
  redirection OAuth sur le nouveau nom.
- **GitHub** : variable `PREPROD_HOST` de l'environnement `preprod` passée à
  `preprod.agorapilot.fr`, puis relance de « Deploy preprod » sur `develop`
  ([run 44950061000](https://github.com/lpzkjdoi/agorapilot/actions/runs/44950061000)).
  `develop` était identique à ce qui était déployé (`c651170`) : seul le nom
  d'hôte a changé.
- **VPS** : rien à modifier. Traefik (v2.10, resolver `myresolver` en TLS-ALPN)
  a une config générique ; les routeurs et le filtre WireGuard sont portés par
  les labels du conteneur `front`, générés depuis `PREPROD_HOST`, et le `.env`
  est régénéré par le workflow.

Dans le dépôt : remplacement de l'ancien nom dans
`apps/front/proxy.conf.preprod.json`, `apps/front/README.md`,
`docs/configuration-meta.md` et `docs/deploiement-preprod.md`, dont la section
de bascule est réécrite comme faite. Mise à jour de l'item « Domaine » de la
feuille de route du README. Les changelogs passés gardent l'ancien nom.

L'ancien nom n'est **pas** gardé en redirection : la préprod n'est accessible
que par le VPN, la redirection ne servirait à personne. Son enregistrement DNS
chez `chariotte-manager.fr` peut être supprimé.

## Vérification

- Certificat Let's Encrypt `CN=preprod.agorapilot.fr` obtenu au premier
  déploiement (valide jusqu'au 2027-02-19). Le contrôle du workflow a échoué
  cinq fois en connexion (`000`) le temps de l'émission, puis a reçu 200.
- Depuis le VPS via `wg0` (`--interface 10.8.0.1`) : `https://preprod.agorapilot.fr`
  → 200, `/api/channels/facebook/callback` → 302.
- Hors VPN : `https://` et `http://preprod.agorapilot.fr` → 403 (le workflow le
  vérifie aussi depuis le runner).
- `https://preprod.chariotte-manager.fr` → 404, aucun routeur ne le porte plus.
- Non vérifié : une connexion Facebook de bout en bout depuis le navigateur
  (à faire par Maxime, connecté au VPN).

Commit : voir la PR de la branche `claude/preprod-agorapilot-fr`.
