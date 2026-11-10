# Connexion de la Page Facebook en préproduction : ce qu'il fallait côté Meta

## Contexte

Après la suppression du jeton historique (PR #68), la préproduction ne
publiait plus. Aucune connexion depuis l'interface n'avait encore abouti (cf.
[changelog](./2026-11-10-erreur-retour-facebook.md)). Facebook refusait le
retour avec le code `1349048`, « Le domaine de cette URL n'est pas inscrit dans
ceux de l'application ».

## Ce qui a débloqué la connexion

Le même message est revenu jusqu'à ce que **trois** réglages de l'app Meta
(`944918291404883`) soient en place et enregistrés :

1. **Domaines de l'app** : `preprod.chariotte-manager.fr`, une seule fois. Un
   doublon était présent.
2. **Plateforme « Site web »** : URL du site `https://preprod.chariotte-manager.fr/`.
3. **Facebook Login → Paramètres → URI de redirection OAuth valides** :
   `https://preprod.chariotte-manager.fr/api/channels/facebook/callback`. Ce
   réglage se trouve dans un *cas d'utilisation* de l'app
   (`/apps/<id>/use_cases/` → « Personnaliser »), et non dans le menu de gauche.

Avec le domaine seul, l'erreur `1349048` persistait. Elle parle de « domaine »
même quand c'est l'URI de redirection qui manque.

## Résultat

Le 2026-11-10 à 21:22, la Page « Agorapilot tests wououh » (`1144547398751185`)
a été connectée. Le jeton de Page est permanent (pas d'échéance), contrôlé
valide, avec `pages_manage_posts`. Le canal n'est pas suspendu, et l'événement
« Page Facebook connectée » figure au journal de sécurité.

Les diffusions programmées les 11, 12 et 13/11 publieront avec ce jeton.

## Changements de documentation

- [`configuration-meta.md`](../configuration-meta.md) :
  - nouvelle section « En bref » avec les trois réglages et l'erreur que donne
    chacun ;
  - chemin vers Facebook Login par les cas d'utilisation ;
  - plateforme Site web, doublon de domaine et bouton « Enregistrer » ;
  - bandeau « Currently ineligible for submission » (sans effet en mode
    développement) et champs qu'il exige pour le mode Live ;
  - commandes de diagnostic sur le VPS, et nouvelles lignes de dépannage.
- Roadmap : « Connexion du canal Facebook depuis l'interface » et sa validation
  en préproduction cochées. Reste l'option System User.

## Vérification

Lecture seule sur la préproduction : `facebook_connections`, journal de
sécurité, `channel_suspensions`, logs back et nginx.

## Commit

_voir PR_
