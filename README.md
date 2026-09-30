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
3. Lancer l'application (le profil `local` est actif par défaut) :
   ```powershell
   mvn spring-boot:run
   ```
4. L'API est disponible sur `http://localhost:8080`. Flyway crée automatiquement les tables au démarrage.

## Voir les logs

Les logs s'affichent dans la console qui lance `mvn spring-boot:run` (ou dans l'onglet **Run**/**Console** d'IntelliJ si lancé depuis l'IDE).

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

La configuration est répartie entre un fichier commun et un fichier par environnement, sélectionné via `spring.profiles.active` (par défaut : `local`).

| Fichier | Rôle |
|---|---|
| `application.yml` | Configuration commune (nom d'app, JPA/Flyway, port, expiration JWT) |
| `application-local.yml` | Développement sur poste, valeurs en dur alignées avec `docker-compose.yml` |
| `application-dev.yml` | Environnement de développement/recette partagé, valeurs via variables d'environnement |
| `application-prod.yml` | Production, toutes les valeurs sensibles obligatoires via variables d'environnement |

### Changer de profil

Via variable d'environnement :
```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

Ou via argument JVM :
```powershell
java -jar target\fasoecole-1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

### Variables d'environnement par profil

**`local`** : aucune variable requise, tout est préconfiguré (Postgres Docker local).

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
| `SPRING_PROFILES_ACTIVE` | Profil actif (`local`, `dev`, `prod`) | `local` |
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

Les annonces et événements n'ont pas encore de modèle métier dans la base actuelle. Le tableau de bord ne les présente donc pas comme s'ils existaient ; ils pourront être ajoutés avec les modules calendrier et communication.

## Migrations de base de données

Les scripts Flyway se trouvent dans `src/main/resources/db/migration` et sont exécutés automatiquement au démarrage de l'application.
