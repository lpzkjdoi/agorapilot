# Accès à la préproduction restreint au réseau WireGuard

**Date** : 2026-07-25
**Branche** : `claude/preprod-acces-vpn`

## Contexte

La préproduction mise en service le jour même était accessible à tout Internet. Elle ne
comporte aucune authentification applicative (la chaîne de filtres du profil `preprod` est
permissive) et expose Swagger UI : la laisser ouverte n'était pas tenable, même sans données
réelles.

Le VPS dispose déjà de WireGuard en natif — interface `wg0` en `10.8.0.1/24`, port UDP 51820 —
avec des clients en tunnel complet. Le filtrage par IP source était donc la solution la plus
directe.

## Changements

Un middleware Traefik `ipwhitelist` est ajouté sur le conteneur `front`, appliqué aux deux
routeurs. La plage vient de la nouvelle variable `PREPROD_ALLOWED_CIDRS`, dont la valeur par
défaut (`10.8.0.0/24`) est volontairement restrictive : un oubli ferme l'accès au lieu de
l'ouvrir.

Sur le routeur HTTP, le filtre est placé **avant** la redirection vers HTTPS, afin qu'une IP
non autorisée reçoive 403 sans même apprendre qu'une version HTTPS existe.

### Deux détails qui auraient coûté du temps

**Le nom du middleware est `ipwhitelist`, pas `ipallowlist`.** `IPAllowList` n'existe qu'à
partir de Traefik v2.11 ; le VPS tourne en **v2.10**. Le point est commenté dans le compose et
documenté dans le runbook, car le renommage est inverse en v3 : une montée de version cassera
le filtre si on n'y pense pas.

**L'IPv6 aurait pu créer un trou.** Un tunnel WireGuard déclaré en `AllowedIPs = 0.0.0.0/0` ne
capte pas l'IPv6. Le VPS a bien une adresse IPv6, mais le domaine n'a **pas d'enregistrement
AAAA** — vérifié avant d'écrire la configuration. Si un AAAA est ajouté un jour, les clients
sur réseau IPv6 sortiraient du tunnel et seraient rejetés ; le runbook indique les deux
corrections possibles.

## Vérification effectuée

Déployé et testé sur le VPS avant ouverture de la PR :

| Source | Résultat |
|---|---|
| Internet, hors VPN (HTTPS) | **403** |
| Internet, hors VPN (HTTP) | **403** — le filtre passe avant la redirection |
| `--interface 10.8.0.1` depuis le VPS (plage VPN) | **200** |
| `--interface 46.202.175.115` depuis le VPS (hors plage) | **403** |

La poignée de main TLS aboutit toujours pour un client externe : le certificat est bien servi
avant le 403.

### Enseignement : un 404 transitoire au déploiement

Le premier test après recréation du conteneur a renvoyé **404** et non 403, ce qui a d'abord
laissé croire que Traefik refusait le middleware. En réalité le test tombait pendant la
recréation, quand Traefik n'a momentanément plus de routeur pour l'hôte. Le comportement est
désormais documenté dans le runbook, avec la distinction à faire : un 404 transitoire est
normal, un 404 persistant signale un problème de découverte.

### Non vérifié

**Le renouvellement du certificat sous filtre.** Le certificat courant a été émis avant la mise
en place du filtre et expire le 23 octobre 2026 ; le premier renouvellement concerné aura donc
lieu vers fin septembre. Le raisonnement est que le challenge TLS-ALPN est traité pendant la
poignée de main TLS, avant tout routage HTTP, et n'est donc pas soumis au middleware — ce que
conforte le fait que le certificat continue d'être servi aux clients externes. Mais cela reste
à confirmer le jour venu. En cas d'échec, la solution est un challenge **DNS-01**, qui ne
requiert aucune joignabilité publique.

## Suite

La publication des images sur GHCR, qui permettra de réduire `/opt/agorapilot/preprod` au
compose et au `.env`.
