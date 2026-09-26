# Corrections issues de la première mise en service de la préproduction

**Date** : 2026-07-25
**Branche** : `claude/preprod-corrections`

## Contexte

Le premier déploiement manuel de la préproduction sur le VPS a réussi
(cf. [entrée précédente](./2026-07-25-preprod-docker-compose.md)), mais l'exercice a révélé
quatre défauts dans ce qui venait d'être livré. Trois relèvent de la configuration et de la
documentation, le quatrième d'un manque de robustesse qui a coûté deux tentatives à l'aveugle
lors de l'initialisation du token Facebook.

## Changements

### 1. `FACEBOOK_PAGE_ACCESS_TOKEN` était une variable morte

Le champ `accessToken` de `FacebookProperties` n'était lu par aucune ligne de code :
`props.accessToken()` n'apparaissait nulle part. Le token de page n'est pas une donnée de
configuration mais un **état** — obtenu via `POST /admin/facebook/bootstrap`, stocké dans la
table `facebook_tokens`, renouvelé par `FacebookTokenScheduler`. Le test existant
`FacebookTokenServiceTest` le construisait d'ailleurs avec une chaîne vide, ce qui confirmait
son inutilité.

Or `docker-compose.preprod.yaml` la déclarait en `${FACEBOOK_PAGE_ACCESS_TOKEN:?...}`, donc
**obligatoire** : le déploiement exigeait une valeur pour une variable que l'application
ignore. Retirée du compose, de `.env.preprod.example`, de `application.yaml` et de
`FacebookProperties`, avec un commentaire à chaque endroit expliquant où vit réellement le
token — pour que la question ne se repose pas.

### 2. La commande de bootstrap du runbook était fausse

`docs/deploiement-preprod.md` documentait `curl -X POST .../admin/facebook/bootstrap` sans
paramètre, alors que la signature est `bootstrap(@RequestParam String shortLivedToken)` :
telle quelle, la commande renvoyait 400.

La section est réécrite avec la commande correcte et, surtout, avec le piège réellement
rencontré : dans le Graph API Explorer, **le sélecteur « Meta App » doit désigner l'app de
`FACEBOOK_CLIENT_ID`** avant de générer le token, sinon l'échange échoue avec
`The access token does not belong to application <id>`. Le lien vers le débogueur de token
est ajouté pour lever le doute, ainsi que l'explication de l'expiration à dix ans (un token
permanent renvoyé par Facebook avec `expires_at: 0`).

### 3. Les erreurs de l'API Graph remontaient en 500 opaque

Facebook répondait un 400 parfaitement explicite, mais l'endpoint renvoyait un 500 avec une
pile de 80 lignes ; le message utile n'était visible qu'en lisant les logs du conteneur.

`FacebookAdminExceptionHandler` (nouveau) traduit désormais ces échecs, en se limitant à
`FacebookAdminController` pour ne pas capturer les exceptions des autres contrôleurs :

- 4xx en amont → **400**, avec le message et le code d'erreur Facebook extraits du corps de
  la réponse (`{"error":{"message":...,"code":...}}`) ;
- 5xx en amont → **502**, la défaillance n'étant pas imputable à l'appelant ;
- `IllegalStateException` (« No token available » avant tout bootstrap, « Invalid token ») →
  **409**, qui décrit un état incohérent plutôt qu'une requête mal formée.

Si le corps ne respecte pas la forme attendue, il est renvoyé brut : mieux vaut un message
imparfait qu'un message perdu.

### 4. Le changelog précédent annonçait le déploiement comme non vérifié

Sa section « non vérifié localement » est reclassée : ce qui n'a pas pu être testé sur le
poste (faute de Docker) a depuis été validé sur le VPS. Le détail des contrôles réellement
effectués y est consigné.

### Dette documentée au passage

Le runbook gagne une entrée : `renewIfNeeded()` ne consulte que la date d'expiration
*stockée*, jamais la validité réelle du token. Avec un token permanent, le job nocturne ne
fait donc jamais rien — correct tant que le token vaut, mais si Facebook l'invalide
prématurément, rien ne le détecte avant le premier échec de publication. Un `debug_token`
périodique comblerait ce trou.

## Vérification effectuée

- `cd apps/back && mvn -B verify` → **BUILD SUCCESS**, **18 tests** (contre 14 avant), dont
  les 4 nouveaux de `FacebookAdminExceptionHandlerTest` : traduction d'un 400 Graph API en
  400 lisible avec message et code, d'un 503 en 502, conservation du corps brut quand la
  réponse n'a pas la forme attendue, et 409 sur token absent. Le test tourne à contexte
  Spring complet, ce qui prouve que l'advice est bien câblée et pas seulement correcte en
  isolation.
- Le cas de test reprend le message d'erreur réellement reçu de Facebook pendant la mise en
  service, `fbtrace_id` compris.

### Non vérifié

Le redéploiement sur le VPS avec ces corrections n'a pas encore été effectué. Point
d'attention pour la prochaine mise à jour : le `.env` du VPS contient encore une ligne
`FACEBOOK_PAGE_ACCESS_TOKEN`, désormais sans effet. Elle peut être supprimée, le compose ne
la lit plus.
