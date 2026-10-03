# FasoEcole

Plateforme de gestion scolaire (Spring Boot 3.4.1 / Java 21 / PostgreSQL / Flyway).

## Prérequis

- JDK 21
- Maven (`mvn` disponible dans le PATH)
- Docker Desktop (pour la base de données locale)
- DBeaver (optionnel, pour visualiser la base de données)

## Démarrage rapide (profil local)

1. Démarrer la base de données Postgres avec Docker :
   ```powershell
   docker compose up -d
   ```
2. Compiler le projet :
   ```powershell
   mvn compile
   ```
3. Lancer l'application en activant explicitement le profil Spring `local` :
   ```powershell
   mvn test spring-boot:run -Dspring-boot.run.profiles=local
   ```
4. L'API est disponible sur `http://localhost:8080`. Flyway crée automatiquement les tables au démarrage.

## Voir les logs

Les logs s'affichent dans la console Maven. Dans IntelliJ, configure les objectifs Maven `test spring-boot:run -Dspring-boot.run.profiles=local` et le répertoire de travail `C:\Mes Projets\Faso Ecole\fasoecole-bff` (le dossier qui contient `pom.xml`).

## Visualiser la base de données avec DBeaver

Créer une nouvelle connexion PostgreSQL avec :

| Paramètre | Valeur |
|---|---|
| Host | `localhost` |
| Port | `5433` |
| Base de données | `fasoecoleBD` |
| Utilisateur | `fasoecole_bd_user` |
| Mot de passe | `fasoecole_bd_password` |

## Profils Spring (`application.yml`)

La configuration est répartie entre un fichier commun et un fichier par environnement, sélectionné via `spring.profiles.active`. `application.yml` ne sélectionne aucun profil ; celui-ci doit être précisé au lancement ou fourni par la variable `SPRING_PROFILES_ACTIVE`.

| Fichier | Rôle |
|---|---|
| `application.yml` | Configuration commune (nom d'app, JPA/Flyway, port, expiration JWT) |
| `application-local.yml` | Développement sur poste, valeurs en dur alignées avec `docker-compose.yml` |
| `application-dev.yml` | Environnement de développement/recette partagé, valeurs via variables d'environnement |
| `application-prod.yml` | Production, toutes les valeurs sensibles obligatoires via variables d'environnement |

### URL publiques prévues

| Profil | SPA (origine CORS et liens de réinitialisation) | API |
|---|---|---|
| `dev` | `https://app.dev.fasoecole.com` | `https://api.dev.fasoecole.com/api` |
| `prod` | `https://app.fasoecole.com` | `https://api.fasoecole.com/api` |

`fasoecole.com` est un domaine proposé, non encore acquis ni configuré sur AWS. Les profils `dev` et `prod` utilisent ces URL du SPA par défaut pour `CORS_ALLOWED_ORIGINS` et `FRONTEND_BASE_URL` ; les définir dans chaque environnement AWS si le domaine définitif diffère. `CORS_ALLOWED_ORIGINS` attend l'origine HTTPS du SPA (sans `/api` ni barre finale) et `FRONTEND_BASE_URL` la base des liens de réinitialisation du mot de passe. Le profil `local` reste sur `http://localhost:4200`.

### Changer de profil

Via variable d'environnement :
```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn test spring-boot:run
```

Ou via argument JVM :
```powershell
java -jar target\fasoecole-1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

### Variables d'environnement par profil

**`local`** : les paramètres PostgreSQL sont préconfigurés pour Docker local ; le profil doit être activé explicitement.

**`dev`** :

| Variable | Description | Défaut |
|---|---|---|
| `DB_HOST` | Hôte Postgres | `localhost` |
| `DB_PORT` | Port Postgres | `5432` |
| `DB_NAME` | Nom de la base | `fasoecoleBD` |
| `DB_USER` | Utilisateur Postgres | `fasoecole_bd_user` |
| `DB_PASSWORD` | Mot de passe Postgres | *(requis)* |
| `JWT_SECRET` | Clé secrète JWT (base64) | *(requis)* |

**`prod`** :

| Variable | Description | Défaut |
|---|---|---|
| `DB_HOST` | Hôte Postgres | *(requis)* |
| `DB_PORT` | Port Postgres | `5432` |
| `DB_NAME` | Nom de la base | *(requis)* |
| `DB_USER` | Utilisateur Postgres | *(requis)* |
| `DB_PASSWORD` | Mot de passe Postgres | *(requis)* |
| `JWT_SECRET` | Clé secrète JWT (base64) | *(requis)* |
| `DB_POOL_SIZE` | Taille du pool de connexions Hikari | `10` |

**Variables communes (tous profils)** :

| Variable | Description | Défaut |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Profil actif (`local`, `dev`, `prod`) | *(à définir au lancement)* |
| `SERVER_PORT` | Port HTTP de l'application | `8080` |
| `JWT_EXPIRATION_MS` | Durée de validité du token JWT (ms) | `86400000` (24h) |

> ⚠️ En production, ne jamais réutiliser le secret JWT des profils `local`/`dev`. Générer une clé aléatoire encodée en base64 (256 bits minimum).

## Architecture du code

```
src/main/java/org/afritechinnovations/
├── config/         # Configuration Spring (sécurité, etc.)
├── security/        # JWT (génération, validation, filtre)
├── model/            # Entités JPA, par domaine (academic, common, communication, finance, people)
├── repository/        # Repositories Spring Data JPA
├── dto/               # Objets de transfert (DTO), par domaine
├── service/           # Logique métier, par domaine
└── controler/         # Contrôleurs REST, par domaine
```

## Authentification

- `POST /api/auth/register` : envoyer une demande de compte pour un établissement (le compte reste bloqué jusqu'à validation par son propriétaire)
- `POST /api/auth/login` : obtenir un token JWT
- `GET /api/schools/registration-options` : établissements actifs proposés dans le formulaire public d'inscription
- Les autres routes `/api/**` nécessitent un header `Authorization: Bearer <token>`

## Demandes de compte et réinitialisation du mot de passe

- `POST /api/auth/forgot-password` : demander un lien de réinitialisation
- `POST /api/auth/reset-password` : remplacer le mot de passe avec le jeton reçu par e-mail
- `GET /api/users/pending` : lister les demandes adressées à ses établissements (propriétaire) ou toutes les demandes (super administrateur)
- `POST /api/users/{id}/approve` : attribuer un établissement et un rôle (`TEACHER`, `PARENT` ou `STUDENT`) et activer le compte
- Une inscription choisit un établissement et un profil. Le nouvel utilisateur est connecté avec un accès limité à son statut ; les API métier restent interdites jusqu'à l'approbation.
- L'espace se met à jour automatiquement toutes les 20 secondes. Dès l'approbation, le rôle réel accordé par le propriétaire détermine les menus et l'accès métier.

Pour envoyer les liens de réinitialisation avec Gmail, activez la validation en deux étapes sur le compte expéditeur et créez un **mot de passe d'application Google**. Ne placez pas ce mot de passe dans Git et ne l'envoyez pas dans le chat. Définissez ces variables d'environnement avant de démarrer le backend :

| Variable | Valeur de test |
|---|---|
| `SMTP_HOST` | `smtp.gmail.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | Adresse Gmail expéditrice |
| `SMTP_PASSWORD` | Mot de passe d'application Google |
| `MAIL_FROM` | Adresse Gmail expéditrice |
| `SMTP_AUTH` | `true` |
| `SMTP_STARTTLS` | `true` |
| `FRONTEND_BASE_URL` | `http://localhost:4200` |

Pour IntelliJ IDEA, copiez `env-variable-local.env.example` vers `local.env` à la racine du backend et remplacez l'adresse et le mot de passe d'application. Le fichier `.env` local est ignoré par Git. Dans **Run → Edit Configurations**, ouvrez la configuration Maven et ajoutez ce fichier dans **Environment variables**. Gardez les objectifs `test spring-boot:run -Dspring-boot.run.profiles=local` et le répertoire de travail du backend (celui qui contient `pom.xml`). Le champ **Profiles** d'IntelliJ active des profils Maven, pas des profils Spring. `src/main/resources/env-variable-local.json` n'est pas un fichier `.env` chargeable par IntelliJ.

Le lien expire après 30 minutes par défaut (`PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES`). Les jetons sont générés aléatoirement et seul leur hash est stocké. La réponse de demande de réinitialisation reste identique qu'un compte existe ou non.

Lors de l'inscription, l'utilisateur choisit l'établissement concerné afin que seul son propriétaire reçoive la demande. Le propriétaire choisit le rôle et peut affecter le compte à l'un de ses établissements lors de l'approbation.

Pour tester la réception, inscrivez `boubacar.sawadogo02@gmail.com` et demandez une réinitialisation après approbation du compte. L'adresse SMTP est l'expéditeur ; ne configurez pas une redirection globale des messages vers l'adresse de test.

### Comptes locaux de démonstration

Le profil `local` ajoute des établissements et des comptes exemples via `db/local`. Tous utilisent le mot de passe `password123` :

| État | Adresse | Établissement / profil |
|---|---|---|
| Approuvé | `demo.enseignant@fasoecole.com` | Lycée Wendpanga / enseignant |
| Approuvé | `demo.parent@fasoecole.com` | École primaire La Réussite / parent |
| Approuvé | `demo.etudiant@fasoecole.com` | Université du Faso / étudiant |
| Approuvé | `demo.formation@fasoecole.com` | Centre de Formation professionnelle / enseignant |
| En attente | `attente.enseignant@fasoecole.com` | École primaire La Réussite / enseignant |
| En attente | `attente.parent@fasoecole.com` | Lycée Wendpanga / parent |
| En attente | `attente.etudiant@fasoecole.com` | Université du Faso / étudiant |

Le compte propriétaire local est `admin@fasoecole.com` avec le mot de passe `password123`. Il peut approuver les demandes depuis **Demandes de compte**.

### Tableau de bord propriétaire

`GET /api/owner/dashboard?schoolId={id}` fournit les effectifs et indicateurs de l'établissement connecté : élèves, enseignants, classes, niveaux, parents, demandes de compte, encaissements, solde restant sur les factures ouvertes, présences du jour, bulletins validés, messages non lus et notifications récentes. L'API vérifie côté serveur que l'établissement appartient bien au propriétaire connecté. Les revenus et impayés sont calculés depuis les lignes de paiement et de facture existantes ; ils ne sont pas des données de démonstration calculées côté interface.

La situation financière affiche le total attendu (somme des frais non annulés), l'encaissé (tous les paiements réellement enregistrés) et le reste à recouvrer (solde des frais ouverts). Dans la fiche élève, un frais annulé reste visible avec un solde à payer nul ; ses éventuels paiements déjà encaissés restent comptabilisés dans l'encaissé et l'historique. Dans ce cas, l'encaissé et le reste à recouvrer ne totalisent pas forcément le montant attendu : l'annulation ne rembourse pas les paiements antérieurs.

Les annonces destinées aux parents se publient dans **Portail parents**. Le tableau de bord général ne les affiche pas encore ; les événements peuvent être annoncés dans ce portail, sans calendrier d'événements distinct.

## Messagerie et pièces jointes

Dans **Messages**, le propriétaire choisit les destinataires de l'établissement : enseignants,
élèves, parents, ou une combinaison des trois. La sélection peut être individuelle ou par groupe.
Tous les participants d'une conversation de groupe voient ses destinataires et ses messages.
Les élèves disposent aussi d'une boîte de réception et peuvent répondre.

Les nouveaux messages et les réponses acceptent jusqu'à **3 pièces jointes de 10 Mo chacune** :
PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, ODT/ODS/ODP, TXT, CSV, JPG/JPEG et PNG.
Un message peut contenir uniquement des pièces jointes. Le téléchargement nécessite une session
authentifiée et l'accès à la conversation ; les fichiers n'ont pas d'URL publique.

Les fichiers sont conservés dans PostgreSQL. La migration **V26** sépare les métadonnées du
contenu binaire pour charger les fils de discussion sans charger tous les documents.
Les API JSON existantes restent disponibles ; l'envoi avec fichiers utilise `multipart/form-data`
avec une partie `request` en JSON et des parties `files`.

## Migrations de base de données

### Modules de l'espace parent (V27)

Le menu parent comporte désormais **Notes et bulletins**, **Présences et retards**, **Frais et paiements**,
**Emploi du temps**, **Devoirs et évaluations**, **Annonces**, **Documents** et **Rendez-vous**.
Chaque écran propose un sélecteur parmi les enfants accessibles au parent. Les dossiers existants restent disponibles.
Les bulletins publiés et les reçus de paiements enregistrés peuvent être imprimés ou enregistrés en PDF avec le navigateur.
Le module de paiements consulte les encaissements enregistrés ; il n'effectue pas de paiement en ligne.

Dans l'espace propriétaire, **Portail parents** permet de publier une annonce, un devoir avec date de remise,
ou des documents. Une publication peut viser tout l'établissement, une classe ou un enfant de la classe.
Pour un certificat ou un autre document personnel, sélectionner l'enfant concerné afin de limiter l'accès à ses parents.
Les fichiers acceptés sont les mêmes que dans la messagerie : 3 pièces jointes de 10 Mo maximum.
Les téléchargements nécessitent l'authentification et un accès parent actif à l'enfant ; les fichiers restent dans PostgreSQL.
Un document individuel reste rattaché à l'enfant s'il change de classe dans le même établissement.

Les parents proposent une date et un interlocuteur (administration ou enseignant de l'enfant).
L'établissement coordonne la rencontre et confirme ou refuse la demande avec une réponse visible au parent.
Le parent peut annuler une demande en attente ou confirmée. Le bouton **Actualiser** recharge les réponses.
Ce flux ne synchronise pas de calendrier externe et ne réserve pas automatiquement un créneau enseignant.

La migration **V27** crée les publications, leurs fichiers et les demandes de rendez-vous.
Redémarrer le backend pour que Flyway applique cette migration avant d'utiliser les nouveaux modules.
Les API se trouvent sous `/api/me/parent-portal/students/{studentId}` et `/api/owner/parent-portal/schools/{schoolId}`.
Les actions de l'établissement requièrent le module `STUDENTS` ; les consultations parent vérifient le rattachement réel
et l'accès actif à l'établissement, y compris si le champ descriptif « lien avec l'enfant » est vide.

### Synchronisation et calcul des présences

L'accueil propriétaire/administration, la fiche élève, les présences et signalements enseignant,
les dossiers enfants et le portail parent utilisent une connexion HTTP authentifiée partagée par établissement.
La migration **V30** maintient une révision en base pour les présences, signalements, élèves, classes,
enseignants, rattachements, factures, paiements, publications et rendez-vous. Une modification annulée
par la transaction ne déclenche pas d'actualisation. Le backend consulte ces révisions chaque seconde ;
les écrans concernés rechargent leurs données après validation. Un rechargement de secours intervient
toutes les 30 secondes et une reconnexion est tentée après 5 secondes en cas d'erreur.
Il s'agit d'une actualisation automatique avec un léger délai, dépendant du réseau et du temps des requêtes.

**Présences du jour** compte les saisies de la date courante du serveur, rattachées à la classe
de l'établissement sélectionné. Le total est la somme des statuts PRESENT, ABSENT, LATE et EXCUSED.
Une saisie manquante n'est jamais une absence. Les chiffres du tableau de bord sont lus dans une même
transaction à isolation REPEATABLE_READ pour éviter des totaux contradictoires pendant une modification.
La date dépend du fuseau horaire du serveur ; configurer celui-ci selon la date métier attendue.

La migration **V29** empêche plusieurs saisies pour le même élève, la même classe et la même date.
Lors de sa première application, les anciennes lignes en double sont copiées dans
`attendance_duplicate_archive` puis consolidées en conservant la ligne avec l'identifiant le plus élevé.
Cette règle ne garantit pas que cette ligne contient la dernière correction humaine ; les archives
permettent de contrôler les cas historiques. Les créations répétées deviennent des mises à jour ;
une collision concurrente reste protégée par la contrainte d'unicité de la base.

Les vues ont des périodes et définitions distinctes : le tableau de bord montre la journée ; le résumé
parent porte sur l'année scolaire de l'inscription retenue et regroupe ABSENT et EXCUSED dans les absences ;
la fiche élève récapitule son historique. Un signalement parent en attente n'est pas une présence enregistrée.
Les effectifs du tableau de bord comptent les dossiers/classes de l'établissement, toutes années confondues.

Les montants attendus et restants du tableau de bord déduisent les remises et excluent les factures annulées.
Le reste est plafonné à zéro par facture. Le nombre de factures à payer dépend de leur solde réel.
Les encaissements représentent les paiements enregistrés, y compris ceux d'une facture annulée ensuite.
La messagerie, les notes et l'emploi du temps ne disposent pas de notifications dédiées dans ce mécanisme.

Redémarrer le backend pour appliquer V29/V30, puis recharger le frontend. Une compilation seule ne vérifie
ni l'exécution des migrations ni les valeurs de la base réelle.

### Exécution des migrations

### Parcours et sécurité des comptes

| Création | Vérification et mot de passe | Mise en service |
| --- | --- | --- |
| Inscription publique enseignant, parent ou élève | Le titulaire choisit son mot de passe puis confirme son courriel | Approbation du propriétaire ; le parent lié à un enfant peut recevoir un accès automatique tracé |
| Enseignant créé par l'établissement | Invitation par courriel ; le titulaire choisit son mot de passe | Profil et affectation classe/matière créés par l'établissement |
| Élève créé par l'établissement | Invitation par courriel ; le titulaire choisit son mot de passe | Dossier, matricule, année et inscription active créés ensemble |
| Parent ajouté à un enfant | Invitation si un courriel est fourni ; réutilisation du compte existant par courriel | Rattachement explicite à l'enfant ; preuve du courriel exigée pour l'accès automatique |
| Personnel administratif | Invitation ou notification d'accès pour un compte déjà vérifié | Modules limités par la délégation du propriétaire |
| Compte créé directement par la plateforme | Invitation ; aucun mot de passe transmis par l'administrateur | Les rôles sont attribués séparément par la plateforme |

Les rôles `SUPER_ADMIN`, `SCHOOL_ADMIN` et `STAFF` ne sont jamais acceptés dans l'inscription publique,
une demande d'activation ou une approbation publique. Les comptes actifs mais incomplets peuvent
consulter/modifier leur profil, changer leur mot de passe et renvoyer le lien ; les API métier exigent
un compte actif, approuvé, un courriel vérifié et un mot de passe défini. L'interface les dirige vers
**Mon profil**, qui affiche les étapes restantes (courriel, approbation, enfant, classe ou matière).

L'approbation publique d'un élève ou enseignant nécessite désormais un identifiant attribué par
l'établissement et rattache le compte au dossier correspondant. La classe et les affectations du
dossier existant sont conservées ; l'approbation ne crée pas une nouvelle inscription ni un dossier
en doublon. Une inscription ou un transfert scolaire reste une opération explicite de l'établissement.
Une réinitialisation reste possible pendant l'attente d'approbation : elle prouve le courriel mais
n'accorde pas à elle seule les droits métier.

Les liens utilisent des jetons aléatoires dont seul le hash SHA-256 est stocké. Ils expirent, sont
liés au courriel destinataire et consommés sous verrou de base pour empêcher deux utilisations
simultanées. Une adresse modifiée invalide les anciens liens. Un changement ou une réinitialisation
du mot de passe augmente la version de session : les anciens JWT sont refusés, et le frontend
déconnecte la session révoquée. Une réinitialisation concurrente à la connexion ne peut pas produire
un nouveau JWT valide avec l'ancien mot de passe.

Le résultat des invitations est visible dans les listes élèves/enseignants et les demandes de compte :
`SENT` signifie que le serveur SMTP a accepté l'envoi, **pas** que le destinataire l'a reçu dans sa boîte.
`FAILED` permet de repérer un échec et de renvoyer un lien. Le personnel dispose de **Envoyer un lien** ;
aucun mot de passe provisoire n'est affiché ou envoyé. Le champ API historique `temporaryPassword`
reste `null`. La remise SMTP reste synchrone ; une reprise manuelle génère un nouveau lien et remplace
l'ancien. Il ne s'agit pas d'une file d'envoi avec reprise automatique après arrêt du serveur.

L'inscription publique retourne toujours `202` et la même réponse sans JWT, que le compte soit
nouveau ou déjà connu. Les protections locales limitent à 20 connexions/minute et 10 appels aux
parcours de liens/minute par IP, plus 3 envois de liens/minute par compte (vérification, activation et
réinitialisation réunies). Une limite d'envoi conserve le précédent lien valide. Les compteurs sont
en mémoire et propres à chaque instance : un déploiement avec plusieurs instances doit utiliser
un limiteur partagé ou la passerelle. Le filtre ne fait pas confiance à un `X-Forwarded-For` envoyé
par le client ; la configuration d'un proxy de confiance doit être traitée au déploiement.

**Migration V31 :** redémarrer le backend pour l'appliquer. Les comptes et anciens jetons sont
conservés ; les anciens liens sans destinataire enregistré sont refusés et doivent être renvoyés.
Les comptes déjà présents dont le courriel n'est pas vérifié doivent le confirmer depuis **Mon profil**.
La migration n'active aucun profil Spring et ne remplace aucune configuration d'environnement.

### Tests du parcours de compte

```powershell
# Dans fasoecole-bff
mvn test

# Dans fasoecole-spa
npm test -- --watch=false
npm run build:prod
```

Les suites couvrent les trois profils publics, les invitations de l'école et du personnel, la preuve
du courriel, les approbations, les dossiers et classes, les accès interdits, les erreurs SMTP, les
liens expirés/remplacés/réutilisés, les limites de tentatives et les sessions révoquées. Les tests HTTP
emploient la vraie chaîne Spring Security et le fournisseur d'authentification ; les tests composés
du parcours utilisent le vrai hachage BCrypt et les services de compte. La persistance et SMTP sont
simulés dans ces tests : une réception Gmail réelle et les courses de transactions PostgreSQL ne
sont pas démontrées par les tests unitaires. La migration V31 a aussi été vérifiée séparément sur
des tables temporaires PostgreSQL, avec annulation de la transaction.

Les workflows GitHub Actions exécutent déjà ces suites avant de construire les applications.
Les tests Angular utilisent au maximum deux workers pour limiter la consommation mémoire de jsdom.
Le budget de styles par composant est de 8 ko (avertissement) et 12 ko (erreur), adapté aux modules
actuels ; le budget du chargement initial reste inchangé.

### Modules de l'espace enseignant

Le menu enseignant comporte **Mes classes**, **Notes**, **Mon emploi du temps**, **Présences et retards**,
**Signalements des parents**, **Devoirs**, **Documents de classe**, **Annonces de classe** et **Rendez-vous parents**.
Les actions par classe nécessitent une affectation active de l'enseignant connecté ; un identifiant de classe modifié
dans la requête ne donne pas accès aux classes d'un autre enseignant.

Les présences se saisissent par élève et par date passée ou courante, dans l'année scolaire de la classe.
Une ligne sans statut reste « Non renseigné » jusqu'à son enregistrement.
Dans **Signalements des parents**, sélectionner la date puis **Enregistrer retard** ou **Enregistrer absence**
pour inscrire la présence et passer le signalement à « Prise en compte ».

Les devoirs, documents et annonces se publient pour les parents de la classe sélectionnée, avec les mêmes pièces
jointes que le portail parent. L'enseignant ne peut retirer que ses propres publications.
Les demandes de rendez-vous visibles sont celles qui lui sont adressées pour les enfants de ses classes.
Il peut confirmer ou refuser une demande ; le parent et l'établissement voient la même réponse.

Ces modules utilisent la migration V27 pour les publications et rendez-vous. La migration **V28** porte les motifs
de présence à 500 caractères, comme les signalements parent, afin d'éviter un échec lors de leur prise en compte.
Redémarrer le backend après la mise à jour pour appliquer les migrations.

Les scripts Flyway se trouvent dans `src/main/resources/db/migration` et sont exécutés automatiquement au démarrage de l'application.

La migration V6 ajoute les types `PRESCOLAIRE` et `MIXTE` sans modifier les établissements, niveaux ou classes existants. Un établissement mixte peut activer des niveaux du préscolaire au lycée ; les autres types restent disponibles. Chaque classe doit utiliser une année scolaire et un niveau appartenant au même établissement. Le catalogue proposé dans la gestion de l'établissement n'ajoute un niveau qu'à la demande du propriétaire. Les universités peuvent activer les niveaux Licence, Master et Doctorat ; les centres de formation peuvent activer CAP, BEP, BT et BTS. Ces suggestions sont des niveaux, pas encore un modèle de filière ou de diplôme.

## Inscription parent et matricules des enfants

- À l’inscription publique, un parent indique l’établissement et au moins un matricule de ses enfants dans cet établissement (maximum 20, 50 caractères chacun). Les matricules sont du texte : les zéros initiaux sont conservés et les doublons supprimés.
- La demande conserve les matricules déclarés ; aucune recherche de noms d’enfants n’est révélée au demandeur et aucun lien parent–enfant n’est créé à ce stade.
- Dans **Demandes de compte**, l’admin autorisé voit les matricules, les noms correspondants et les matricules introuvables. Il doit vérifier le lien familial : connaître un matricule ne prouve pas la parentalité.
- À l’approbation, tous les matricules doivent exister dans l’établissement demandé. Le dossier parent et les liens manquants sont créés dans la même transaction que l’accès à l’établissement ; les liens existants sont conservés sans doublons. Un matricule introuvable bloque toute l’approbation.
- Un parent peut corriger ses matricules initiaux dans **Mon profil** tant que son inscription est en attente. Les anciennes demandes sans matricule doivent être complétées, sauf si des liens vers les enfants de cette école existent déjà.
- Depuis l’accueil parent, **Ajouter un établissement** mène à la demande d’accès dans le profil. Le parent choisit la nouvelle école et renseigne les matricules de ses enfants dans cette école. L’accès et les associations nécessitent son approbation ; une demande supplémentaire erronée peut être annulée puis recréée.
- Les invitations à un compte existant conservent leurs matricules dans le jeton d’activation et ne modifient les informations du compte qu’après preuve du courriel. Un renvoi conserve ce contexte.
- La migration **V32** ajoute trois tables de déclarations (inscription, demande supplémentaire, activation), sans modifier les associations existantes. Redémarrer le backend pour l’appliquer via Flyway.

Les tests couvrent l’inscription, la preuve du courriel, l’approbation, l’association des enfants, les demandes supplémentaires, la confidentialité des correspondances, les matricules inconnus et les liens déjà existants. Les repositories et SMTP sont simulés dans les tests unitaires.

## Matricule élève et numéro d’employé à l’inscription

- À l’inscription publique, les profils **STUDENT** et **TEACHER** doivent fournir `schoolIdentifier` : respectivement leur matricule et leur numéro d’employé, propres à l’établissement choisi. La demande d’accès à une autre école exige aussi le numéro dans cette autre école.
- Le champ reste du texte (maximum 50 caractères, zéros initiaux conservés). Les correspondances nominatives sont réservées à la revue de l’admin autorisé : aucune recherche publique n’expose le répertoire des personnes.
- L’approbation exige un dossier existant avec ce numéro dans cette école. Le rattachement conserve le dossier, ses classes, ses affectations et les autres données. Un numéro inconnu, un dossier associé à un compte ayant choisi un mot de passe ou invité avec un autre courriel bloque l’approbation.
- Un dossier sans courriel et sans mot de passe choisi peut être rattaché après vérification explicite de l’identité par l’admin. Son ancien accès technique à cette école est retiré sans supprimer le compte ni ses éventuels autres accès.
- Le titulaire peut corriger son identifiant initial dans **Mon profil** tant que le compte est en attente. Les anciennes demandes sans numéro doivent être complétées avant approbation.
- Pour un nouvel admis sans numéro, l’établissement crée le dossier et envoie une invitation. La création scolaire peut aussi réutiliser le compte public en attente avec le même courriel, le même profil et la même école, sans remplacer son mot de passe ni l’approuver automatiquement.
- Les enseignants ont maintenant un **numéro d’employé**, unique par école, visible dans leur liste et saisissable lors de la création scolaire. La migration **V33** attribue `EMP-<id>` aux dossiers existants ; les nouveaux dossiers sans numéro saisi en reçoivent un automatiquement.
- Les invitations scolaires restent un parcours administrateur déjà autorisé ; leur confirmation de courriel et le choix de mot de passe restent nécessaires. Les numéros ne remplacent jamais la vérification d’identité, la preuve du courriel ni l’approbation.
- Les demandes invalides peuvent encore être soumises : le numéro bloque l’approbation et l’accès, tandis que la limitation des tentatives freine les soumissions automatiques.

Redémarrer le backend pour appliquer **V33**, puis recharger le frontend. Les tests unitaires couvrent les identifiants obligatoires, les dossiers inconnus, les écoles distinctes, les risques de prise de contrôle, la réutilisation des dossiers, les comptes en attente et la conservation des identifiants dans l’activation. La persistance et SMTP restent simulés dans ces tests.
