# Configurer l'app Meta pour le canal Facebook

AgoraPilot publie sur une Page Facebook grâce à une **app Meta**. Celle-ci
fournit l'identifiant et le secret qu'utilise le back. Elle autorise aussi
l'adresse de retour de la connexion et porte les permissions demandées à la
personne qui connecte la Page.

Cette page décrit la configuration à faire **une fois par environnement**
(préproduction, production, poste local). La connexion elle-même se fait
ensuite depuis l'interface (page « Canal Facebook »), cf.
[`deploiement-preprod.md`](./deploiement-preprod.md#connexion-du-canal-facebook-depuis-linterface).

> Les libellés de la console Meta changent souvent et dépendent de la langue du
> compte. Ce qui compte, c'est le résultat attendu à chaque étape, rappelé en gras.

## Ce qu'il faut obtenir

| Élément | Où il sert | Exemple |
|---|---|---|
| Identifiant de l'app (App ID) | `FACEBOOK_CLIENT_ID` (Variable de l'Environment GitHub) | `1234567890123456` |
| Clé secrète de l'app (App Secret) | `FACEBOOK_CLIENT_SECRET` (**Secret** de l'Environment GitHub) | — |
| Identifiant de la Page | `FACEBOOK_PAGE_ID` (Variable) | `1144547398751185` |
| Version de l'API Graph | `FACEBOOK_API_VERSION` (Variable, défaut `v25.0`) | `v25.0` |
| URI de redirection OAuth | déclarée dans l'app Meta | `https://<PREPROD_HOST>/api/channels/facebook/callback` |
| Permissions | demandées par AgoraPilot à la connexion | `pages_show_list`, `pages_read_engagement`, `pages_manage_posts` |

## 1. Créer l'app

Sur [developers.facebook.com](https://developers.facebook.com/apps) → « Mes
apps » → « Créer une app ». Choisir le cas d'usage de **gestion d'une Page** (ou
le type « Entreprise » selon la version de la console). Le rattacher au
portefeuille Business qui possède la Page, s'il existe.

**Résultat attendu** : une app qui propose le produit **Facebook Login**
(« Facebook Login for Business » selon la console).

Une seule app peut servir à plusieurs environnements : il suffit d'ajouter
l'URI de redirection de chacun (étape 3). Une app distincte pour la production
évite en revanche qu'un test de préproduction n'agisse avec les droits de la
prod.

## 2. Paramètres de base

App → Paramètres → Général (« Paramètres de base ») :

- relever l'**identifiant** et la **clé secrète** de l'app ;
- **domaines de l'app** : le domaine de l'environnement (`<PREPROD_HOST>`, par
  exemple `preprod.chariotte-manager.fr`). **Obligatoire** : sans lui, Facebook
  refuse la connexion avant même d'afficher la boîte de dialogue (« Le domaine de
  cette URL n'est pas inscrit dans ceux de l'application ») ;
- **URL de la politique de confidentialité** : requise pour passer l'app en
  mode Live (étape 6).

## 3. Facebook Login : URI de redirection

App → Facebook Login → Paramètres :

- **Connexion OAuth cliente** et **Connexion OAuth web** : activées ;
- **Imposer HTTPS** : activé ;
- **URI de redirection OAuth valides** : ajouter, **à l'identique**,
  `<FRONTEND_URL>/api/channels/facebook/callback`, soit :

  ```
  https://<PREPROD_HOST>/api/channels/facebook/callback
  ```

  Le back construit cette adresse à partir de `FRONTEND_URL`, que
  `docker-compose.preprod.yaml` pose à `https://${PREPROD_HOST}`. Un écart
  (http au lieu de https, barre oblique finale, autre domaine) suffit à faire
  refuser la connexion par Facebook (« URL bloquée »).

Le VPN de la préproduction ne gêne pas : la redirection passe par le navigateur
de la personne qui connecte, jamais par les serveurs de Facebook.

**Résultat attendu** : l'URI figure dans la liste, une ligne par environnement.

## 4. Permissions et rôles

AgoraPilot demande trois permissions à la connexion
(`FacebookConnectionService.SCOPES`) :

| Permission | Pourquoi |
|---|---|
| `pages_show_list` | lister les Pages du compte pour y trouver `FACEBOOK_PAGE_ID` |
| `pages_read_engagement` | lire les informations de la Page |
| `pages_manage_posts` | **publier** ; son absence suspend le canal au contrôle quotidien |

Tant que l'app est en **mode développement**, ou n'a que l'accès standard à ces
permissions, seules les personnes qui ont un **rôle dans l'app** (administrateur,
développeur, testeur : App → Rôles) peuvent la connecter. La personne qui
connecte doit en plus **administrer la Page**.

**Résultat attendu** : le compte Facebook qui cliquera sur « Connecter la Page »
a un rôle dans l'app et administre la Page.

## 5. Identifiant de la Page et configuration de l'environnement

L'identifiant de la Page se trouve sur la Page → « À propos » → « Transparence
de la Page ». Le Graph Explorer (`GET /me/accounts`) le donne aussi.

Reporter les valeurs dans l'Environment GitHub de l'environnement (cf.
[configuration attendue](./deploiement-preprod.md#configuration-attendue)) :

- **Variables** : `FACEBOOK_CLIENT_ID`, `FACEBOOK_PAGE_ID`, `FACEBOOK_API_VERSION` ;
- **Secret** : `FACEBOOK_CLIENT_SECRET`.

Relancer ensuite « Deploy preprod » (`workflow_dispatch`). Sans ces variables,
`docker compose up` s'arrête. En local, les mêmes variables vont dans
l'environnement du back.

Pour connecter la Page : page « Canal Facebook » → « Connecter la Page
Facebook », accepter les permissions demandées, puis « Contrôler maintenant ».

**Résultat attendu** : la page affiche la Page connectée, un jeton **valide** et
la permission `pages_manage_posts`.

## 6. Mode Live (avant la production)

En mode développement, **les posts publiés ne sont visibles que des comptes qui
ont un rôle dans l'app**. Pour une Page publique, l'app doit passer en **mode
Live** (bascule en haut de la console), ce qui demande au moins l'URL de
politique de confidentialité.

Restent **à confirmer dans la console Meta** au moment de la mise en production :

- l'obligation, ou non, de faire examiner l'app (App Review) pour obtenir
  l'accès avancé à `pages_manage_posts`. Si seules des personnes qui ont un rôle
  dans l'app connectent la Page, l'accès standard pourrait suffire ;
- la vérification de l'entreprise (Business Verification), si Meta l'exige
  pour cet accès.

## Dépannage

Le motif d'un refus s'affiche sur la page « Canal Facebook » ; le détail est dans
les logs du back (`Connexion Facebook`).

| Message | Cause probable | Correctif |
|---|---|---|
| « Facebook a refusé la connexion : … le domaine de cette URL n'est pas inscrit dans ceux de l'application … » | Domaine de l'environnement absent des domaines de l'app | Étape 2 : ajouter `<PREPROD_HOST>` aux domaines de l'app |
| « URL bloquée » (sur Facebook) | URI de redirection absente ou différente | Étape 3 : recopier l'URI exacte |
| « Le compte Facebook connecté n'administre pas la Page … » | Mauvais compte, ou `FACEBOOK_PAGE_ID` erroné | Se connecter avec un administrateur de la Page ; vérifier l'identifiant |
| « La permission pages_manage_posts n'a pas été accordée à AgoraPilot. » | Permission décochée dans la boîte de dialogue | Reconnecter en acceptant toutes les permissions |
| « Facebook a refusé la connexion : … » | Secret erroné, app désactivée, compte sans rôle dans l'app | Étapes 2 et 4 |
| « Demande de connexion expirée / inconnue » | Plus de 10 minutes sur Facebook, ou retour rejoué | Recommencer depuis AgoraPilot |
| Posts invisibles pour le public | App en mode développement | Étape 6 |
