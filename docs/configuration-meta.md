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

## En bref : les trois réglages sans lesquels la connexion échoue

Constatés sur la préproduction le 2026-11-10. Il faut **les trois**, et chacun
**enregistré** :

| Où | Quoi | Erreur s'il manque |
|---|---|---|
| Paramètres de l'app → Général → **Domaines de l'app** | `preprod.chariotte-manager.fr` (une seule fois) | `1349048` « Le domaine de cette URL n'est pas inscrit… » |
| Même page, en bas → **Ajouter une plateforme → Site web** | URL du site `https://preprod.chariotte-manager.fr/` | même erreur `1349048` |
| Cas d'utilisation → Personnaliser → **Facebook Login → Paramètres** → URI de redirection OAuth valides | `https://preprod.chariotte-manager.fr/api/channels/facebook/callback` | même erreur `1349048`, même domaine et Site web en place |

Le message de Facebook parle de **domaine** dans les trois cas : ne pas
s'arrêter au premier réglage s'il ne suffit pas.

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
  exemple `preprod.chariotte-manager.fr`), sans `https://` ni barre oblique, et
  **une seule fois** (un doublon peut empêcher l'enregistrement). **Obligatoire** :
  sans lui, Facebook refuse la connexion avant même d'afficher la boîte de
  dialogue (code `1349048`) ;
- tout en bas : **« Ajouter une plateforme » → « Site web »**, URL du site
  `https://<PREPROD_HOST>/` ;
- **« Enregistrer les modifications »**, en bas de page : sans ce clic, rien
  n'est pris en compte ;
- **URL de la politique de confidentialité** : requise pour passer l'app en
  mode Live (étape 6).

Le bandeau rouge « Currently ineligible for submission » (icône, politique de
confidentialité, catégorie manquantes) ne bloque **pas** la connexion en mode
développement : il concerne la soumission à Meta (étape 6).

## 3. Facebook Login : URI de redirection

Selon la console, Facebook Login n'apparaît pas dans le menu de gauche : il est
rangé dans un **cas d'utilisation**.

1. Ouvrir `https://developers.facebook.com/apps/<FACEBOOK_CLIENT_ID>/use_cases/`
   (liste des « Cas d'utilisation » de l'app) ;
2. sur le cas d'utilisation de l'app, **« Personnaliser »** (ou le crayon) ;
3. sous « Facebook Login » (ou « Facebook Login for Business »),
   **« Paramètres »**.

Sur une console plus ancienne : menu de gauche → « Produits » → « Facebook
Login » → « Paramètres ». Si Facebook Login n'existe nulle part, l'ajouter :
« Ajouter un cas d'utilisation » (ou « Ajouter un produit »).

Sur cette page :

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
  refuser la connexion par Facebook (« URL bloquée », ou de nouveau le code
  `1349048`).

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
Si Facebook affiche « vous avez déjà autorisé l'app », continuer : c'est normal
pour un compte qui a déjà connecté l'app.

**Sans Page connectée, rien ne part sur Facebook** : les diffusions échouent avec
le motif « Aucune Page Facebook connectée ». Après la connexion, les reprendre
depuis le calendrier (« Reprendre »).

**Résultat attendu** : la page affiche la Page connectée, un jeton **valide** et
la permission `pages_manage_posts`.

## 6. Mode Live (avant la production)

En mode développement, **les posts publiés ne sont visibles que des comptes qui
ont un rôle dans l'app**. Pour une Page publique, l'app doit passer en **mode
Live** (bascule en haut de la console). Meta exige pour cela, dans Paramètres
→ Général : une **icône** 1024 × 1024, l'**URL de la politique de
confidentialité** et une **catégorie** (c'est ce que liste le bandeau
« Currently ineligible for submission »).

Restent **à confirmer dans la console Meta** au moment de la mise en production :

- l'obligation, ou non, de faire examiner l'app (App Review) pour obtenir
  l'accès avancé à `pages_manage_posts`. Si seules des personnes qui ont un rôle
  dans l'app connectent la Page, l'accès standard pourrait suffire ;
- la vérification de l'entreprise (Business Verification), si Meta l'exige
  pour cet accès.

## Dépannage

Le motif d'un refus s'affiche sur la page « Canal Facebook » ; le détail est dans
les logs du back (`Connexion Facebook`).

Sur le VPS, dans `/opt/agorapilot/preprod/repo` :

```bash
# Motif des refus, côté back
docker compose -f docker-compose.preprod.yaml logs --since 1h back | grep "Connexion Facebook"
# Ce que Facebook a renvoyé au retour (code d'erreur Meta)
docker compose -f docker-compose.preprod.yaml logs --since 1h front | grep -o "facebook/callback?error_code=[0-9]*"
# La connexion a-t-elle abouti ?
docker compose -f docker-compose.preprod.yaml exec -T db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select page_name, connected_by, connected_at, last_check_result from facebook_connections;"'
```

Un journal de sécurité sans « Page Facebook connectée » (page « Comptes ») veut
dire qu'aucune connexion n'a jamais abouti.

| Message | Cause probable | Correctif |
|---|---|---|
| « Facebook a refusé la connexion : … le domaine de cette URL n'est pas inscrit dans ceux de l'application … » (code `1349048` dans les logs nginx) | Un des trois réglages [« En bref »](#en-bref--les-trois-réglages-sans-lesquels-la-connexion-échoue) manque ou n'est pas enregistré | Les vérifier **tous les trois**, enregistrer, réessayer |
| « Demande de connexion inconnue ou déjà utilisée » | Retour rejoué, ou version antérieure à la PR #69 qui masquait l'erreur de Facebook | Recommencer depuis AgoraPilot ; lire le motif réel dans les logs nginx (`/api/channels/facebook/callback?error_message=…`) |
| « URL bloquée » (sur Facebook) | URI de redirection absente ou différente | Étape 3 : recopier l'URI exacte |
| « Le compte Facebook connecté n'administre pas la Page … » | Mauvais compte, ou `FACEBOOK_PAGE_ID` erroné | Se connecter avec un administrateur de la Page ; vérifier l'identifiant |
| « La permission pages_manage_posts n'a pas été accordée à AgoraPilot. » | Permission décochée dans la boîte de dialogue | Reconnecter en acceptant toutes les permissions |
| « Facebook a refusé la connexion : … » | Secret erroné, app désactivée, compte sans rôle dans l'app | Étapes 2 et 4 |
| « Demande de connexion expirée » | Plus de 10 minutes sur Facebook | Recommencer depuis AgoraPilot |
| Posts invisibles pour le public | App en mode développement | Étape 6 |
