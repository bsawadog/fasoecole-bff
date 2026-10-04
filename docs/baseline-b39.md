# Installation d’une nouvelle base avec B39

`src/main/resources/db/migration/B39__initial_schema.sql` contient le schéma
PostgreSQL consolidé après V39 : tables, séquences, contraintes, index, fonctions
et triggers de synchronisation et de protection des années clôturées.

Il initialise uniquement les six rôles : `SUPER_ADMIN`, `SCHOOL_ADMIN`,
`TEACHER`, `PARENT`, `STUDENT`, `STAFF`.

Il ne copie ni les données métier, ni les comptes de démonstration, ni les
mots de passe, ni le compte personnel SUPER_ADMIN, ni l’historique Flyway local.
Les séquences des nouvelles tables commencent à leur valeur initiale.

## Nouvelle installation AWS

1. Créer une base PostgreSQL vide, dédiée au dev.
2. Configurer le profil `dev` et les identifiants de cette nouvelle base.
3. Conserver les migrations B39 et V1 à V39 dans le même répertoire.
4. Pour cette installation, définir
   `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false` afin de refuser une base non vide
   dépourvue d’historique Flyway, au lieu de lui attribuer une version arbitraire.
5. Démarrer le backend. Flyway sélectionne B39 pour une base vide et ignore les
   anciennes migrations versionnées, y compris l’insertion des données de test V2.
6. Flyway exécute ensuite la migration Java V40, qui crée le premier SUPER_ADMIN
   Boubacar Sawadogo, `boubacar.sawadogo02@gmail.com`, téléphone `+14389217823`.
   Son mot de passe initial est aléatoire, haché avec BCrypt et n’est ni affiché
   ni conservé en clair. Aucun mot de passe temporaire n’est à distribuer.
7. Depuis la connexion, utiliser **Mot de passe oublié** avec ce courriel.
   Le service SMTP doit fonctionner et `FRONTEND_BASE_URL` doit désigner le
   frontend dev. Le lien permet de choisir le mot de passe et vérifie le courriel.
8. Se connecter avec le mot de passe choisi pour accéder à l’administration.

Si ce courriel appartient déjà à un SUPER_ADMIN, V40 préserve le compte existant.
S’il appartient à un compte sans ce rôle, V40 refuse l’attribution automatique :
examiner le compte avant toute attribution manuelle. La migration ne réactive
pas un compte désactivé et ne remplace aucun mot de passe existant.

Ne pas appeler `flyway baseline` pour installer B39 sur une base vide : cette
commande marque un état dans l’historique sans créer les tables. Une migration
`B39` est un fichier SQL exécuté par `migrate`, ce qui est différent.

## Base locale existante

Une base possédant déjà des migrations appliquées ignore B39. Les fichiers
V1 à V39 restent nécessaires à la validation de son historique et ne sont
ni supprimés ni réécrits. La prochaine évolution commune sera V40.

Une fois B39 appliquée à un environnement, ne pas modifier son contenu : les
évolutions doivent passer par les nouvelles migrations versionnées.

## Contrôle effectué lors de la préparation

Le schéma a été exporté sans données depuis la base locale dont l’historique
atteint V39. La table `flyway_schema_history`, les instructions spécifiques à
psql, les propriétaires et les privilèges locaux sont exclus du fichier.
Le répertoire `public` existe déjà dans une base PostgreSQL vide et n’est pas
recréé. Les rôles nécessaires sont ajoutés explicitement à la fin.

La compilation copie B39 dans les ressources du backend. L’installation
complète sur une base vide reste à vérifier avant le déploiement AWS.
