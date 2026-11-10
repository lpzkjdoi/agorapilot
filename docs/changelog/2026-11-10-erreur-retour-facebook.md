# Relayer l'erreur de Facebook au retour de la connexion

## Contexte

Après la suppression du jeton historique (cf.
[changelog](./2026-11-10-suppression-bootstrap-facebook.md)), la page « Canal
Facebook » de la préproduction affichait « Aucune Page connectée ». Une tentative
de connexion se soldait par « Demande de connexion inconnue ou déjà utilisée ».

Diagnostic sur la préproduction (lecture seule) :

- `facebook_connections` était vide, et le journal de sécurité ne contenait
  aucun événement `FACEBOOK_CONNECTED` : **aucune connexion n'avait jamais
  abouti**. Ce qui semblait validé le 2026-11-09 était la publication par le
  jeton historique, en repli, jusqu'à sa suppression.
- Au retour, Facebook n'envoyait ni `code` ni `state`, mais
  `error_code` + `error_message` : « Le domaine de cette URL n'est pas inscrit
  dans ceux de l'application ». Le domaine de la préproduction manque dans les
  **Domaines de l'app** de la console Meta.
- Le back vérifiait la demande (`state`) **avant** de lire l'erreur : sans
  `state`, il répondait « demande inconnue », ce qui masquait le vrai motif. Il ne
  lisait d'ailleurs que `error`, et jamais `error_code` ni `error_message`.

## Changements

- `GET /api/channels/facebook/callback` lit les deux formes d'erreur de Facebook :
  `error` + `error_description` (refus de la personne) et `error_code` +
  `error_message` (app mal configurée).
- `FacebookConnectionService.complete` traite l'erreur de Facebook **avant** la
  demande. Un refus (`access_denied`) reste « Connexion annulée sur Facebook. » ;
  toute autre erreur affiche « Facebook a refusé la connexion : » suivi du motif
  de Facebook. La demande éventuelle est supprimée.
- `configuration-meta.md` : le domaine de l'app est signalé comme obligatoire, et
  le message de Facebook ajouté au tableau de dépannage.
- Rectificatifs : roadmap (validation en préproduction décochée), changelogs du
  2026-11-09 et du 2026-11-10.

## Vérification

- Back : `mvn -B verify` (motif relayé sans `state`, demande supprimée sur
  erreur, refus de la personne inchangé, deux formes d'erreur au contrôleur).
- Préproduction : reste à ajouter le domaine dans la console Meta, puis à
  connecter la Page.

## Commit

_voir PR_
