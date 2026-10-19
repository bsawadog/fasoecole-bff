# FasoEcole

## Personnel et accès : annuaire unifié et modification des employés

**Personnel et accès** affiche tous les employés de l’établissement, enseignants compris et même sans module délégué. Une personne qui cumule enseignement et fonction administrative apparaît une seule fois. La liste présente les coordonnées, fonctions, rémunération, modules attribués et statut de l’accès, avec recherche et filtre « avec/sans accès ». La liste « Tous les employés » ouvre le même éditeur.

Le bouton **Modifier** permet de changer prénom, nom, courriel facultatif, téléphone, fonction et salaire du personnel, ainsi que le numéro, la spécialité, la date d’embauche et le tarif daté d’un enseignant. Le tarif enseignant passe par le service existant et conserve ses contrôles sur les mois déjà payés. Une date d’embauche ne peut pas exclure des mois déjà préparés/payés. Les affectations, matières et plannings restent gérés dans la fiche enseignant.

Les modules se cochent indépendamment ; **Tout sélectionner**, **Tout retirer** et **Retirer les accès** facilitent la gestion. Retirer les accès conserve la fiche, l’emploi, les versements et l’historique. Suspendre l’accès concerne les modules de gestion délégués ; l’espace enseignant lié aux affectations est indiqué séparément. Les droits du propriétaire ne se retirent pas par délégation et les identités des autres comptes propriétaires/administrateurs se modifient depuis leur propre profil. La gestion reste réservée au propriétaire de l’établissement ou au super-administrateur.

Un changement de courriel refuse les adresses déjà utilisées, invalide les anciens liens d’activation/réinitialisation et les sessions, puis propose une confirmation de la nouvelle adresse. L’identité est celle du compte partagé, y compris lorsqu’il est aussi enseignant ou parent. Aucun mot de passe n’est affiché ni choisi par le gestionnaire ; le bouton d’envoi transmet un lien au titulaire.

**V53** ajoute `school_staff.access_only` : attribuer des modules à un enseignant crée seulement sa délégation, sans contrat de salaire administratif supplémentaire. Une véritable fonction administrative rémunérée s’ajoute explicitement dans sa fiche. L’API d’annuaire est `GET /api/owner/staff/schools/{school}/employees` ; l’édition utilise `PUT .../employees/{user}` et le lien d’accès `POST .../employees/{user}/send-link`. L’ancienne API des membres du personnel reste disponible pour compatibilité. Redémarrer le backend pour appliquer V53, puis actualiser le frontend.

## Calcul automatique de la paie au Burkina Faso

Dans **Salaires**, préparer le mois, ouvrir la fiche de l’employé puis **Calculer la paie — IUTS et TPA**. Renseigner la composition du brut déjà enregistré, la catégorie, les charges familiales justifiées, les cotisations effectivement retenues, les avances/autres retenues et le statut TPA de l’établissement. **Calculer l’aperçu** ne modifie rien ; **Valider le calcul et le net à payer** conserve les paramètres et actualise les montants à verser. Les derniers paramètres de cet employé sont proposés pour les mois suivants, à vérifier avant chaque validation. Aucun calcul n’est appliqué rétroactivement aux salaires existants.

Le calcul regroupe les rémunérations de toutes les fonctions d’un employé dans un même établissement/mois, applique une seule fois les abattements et le barème, puis répartit exactement les retenues entre les rémunérations. Le brut reste celui du circuit existant : montants préparés du personnel, tarifs et présences des enseignants. Les indemnités saisies sont des composantes de ce brut, pas des suppléments. Les avantages en nature entrent dans les bases fiscales et le coût employeur, sans être versés en espèces. Les cotisations sociales sont saisies en montants réels : aucun taux CNSS universel n’est supposé. Les avances déjà versées doivent correspondre à une retenue justifiée et ne doivent pas être aussi enregistrées comme versement du même net, sous peine de les compter deux fois.

Version initiale **BF-CGI-2026-v1**, pour les mois à compter de janvier 2026 : barème progressif de l’article 112 ; abattement professionnel de 25 % ou 20 % du salaire de base ; retraite fiscalement déductible plafonnée à 8 % du salaire de base ; franchises logement/fonction/transport avec leurs plafonds ; réduction familiale de 0/8/10/12/14 %. La base imposable conserve ses décimales ; l’impôt final est arrondi au franc le plus proche. La TPA est calculée séparément à 3 %, avec abattement CGA de 20 % si éligible et exonération selon le statut explicitement renseigné. L’exonération des entreprises privées d’enseignement doit être confirmée pour l’établissement. Le régime **Vacations d’enseignement** applique la retenue distincte de 2 % prévue par la loi de finances 2026, sans IUTS salarié ni TPA sur cette prestation non salariée ; il ne peut pas être appliqué globalement à un employé qui possède aussi une rémunération du personnel. Les situations mixtes ou hors de ces règles nécessitent une vérification comptable avant saisie.

Sources de référence : [CGI publié par la DGI, articles 106, 111–113 et 227–229](https://dgi.bf/verification/CGI), [loi de finances 2026, article 16](https://www.finances.gov.bf/fileadmin/user_upload/storage/fichiers/jo-N__01-special_Loi_de_finances_2026.pdf). Les hypothèses fiscales, le régime et les arrondis doivent être validés par le responsable de paie avant utilisation déclarative ; une évolution légale nécessite une nouvelle version du calculateur, sans recalculer les anciens bulletins.

La validation est transactionnelle, contrôle les versions et rémunérations de l’aperçu et résiste aux relances après une réponse perdue. Elle se verrouille après le premier versement de l’employé pour le mois. Un tarif modifié avant paiement exige un nouveau calcul ; un tarif qui changerait une paie automatique déjà versée est refusé. La fiche de paie conserve les paramètres, bases, retenues, net, TPA et coût employeur de son édition. Les anciennes éditions restent lisibles et inchangées. Les saisies manuelles antérieures restent disponibles ; après un calcul automatique, les corrections passent par un nouveau calcul global de l’employé.

**Comptabilité :** seuls les versements du net sont enregistrés comme paiements de salaire. Dans **Impôts et taxes → Reprise des salaires**, reprendre ou actualiser séparément l’IUTS, la TPA et les retenues sur vacations (catégorie **Autres**, libellé explicite), puis enregistrer leurs règlements réels. La préparation et le calcul ne créent aucun décaissement. Les cotisations sociales et avances restent à suivre dans leurs circuits dédiés ; elles ne sont pas assimilées à l’IUTS ou à la TPA. Le coût employeur affiché inclut les cotisations patronales renseignées, pas des cotisations inconnues calculées implicitement.

API : `GET /api/owner/payroll/schools/{school}/employees/{user}/calculation?month=AAAA-MM` (derniers paramètres), `POST .../calculation/preview` (aperçu), `POST .../calculation` (validation avec paramètres, versions, bruts et identifiant de requête). Ces opérations utilisent les droits **Salaires**, et les reprises fiscales les droits **Impôts et taxes**. Redémarrer le backend pour appliquer **V52**, puis recharger le frontend.

## Graphique financier de l’accueil propriétaire

Le tableau de bord affiche **Encaissements et dépenses** pour l’établissement et l’année scolaire sélectionnés : total encaissé, total dépensé, différence et deux barres par mois. Les montants exacts sont consultables au survol, au clic ou au clavier ; une table détaillée complète le graphique. Les mois sans mouvement restent à zéro. Le graphique reprend le thème de l’application et défile horizontalement sur mobile.

`GET /api/owner/dashboard?schoolId={id}` fournit `monthlyFinance`, avec les bornes de l’année, les mois et leurs totaux. Les encaissements sont les paiements scolaires reçus selon `payment_date`, y compris les paiements de factures ensuite annulées et les paiements d’anciens frais reçus pendant la période. Les dépenses additionnent les dépenses enregistrées (`expenses`, dont les salaires du personnel) et les versements aux enseignants (`teacher_payments`) selon leur date réelle. Les échéances non payées ne sont pas comptées ; la table de liaison des paiements de salaires n’est pas additionnée une deuxième fois. Les écritures manuelles propres au brouillard de caisse ne font pas partie de ce graphique. La différence est un flux sur la période, pas un bénéfice ni un solde bancaire.

Le graphique est accessible au propriétaire, ou au personnel disposant à la fois de **Frais et paiements** et de **Dépenses**, en plus de l’accès au tableau de bord. Il n’est pas affiché sur la page publique. Une année absente ou inaccessible ne produit pas de série. Le calcul utilise les données réelles ; aucune donnée de démonstration n’est injectée. Redémarrer le backend et recharger le frontend après mise à jour ; aucune migration ni nouvelle dépendance graphique n’est nécessaire.

## Import des élèves, frais et salaires

Le bouton de choix du fichier est bleu. La colonne **Résultat** affiche en vert les lignes « À importer » et « Enregistré », et en rouge les erreurs. Après traitement, une boîte de dialogue confirme la réussite complète ou signale l’échec, y compris un import partiel, avec les détails par ligne. Le bouton **OK** actualise les listes (élèves, enseignants et salaires), tout en conservant les lignes échouées pour reprise. Les fichiers illisibles et les erreurs de validation ouvrent aussi une boîte d’erreur, sans actualiser les listes puisqu’aucune donnée n’a été enregistrée.

Le **courriel est facultatif pour les élèves et les enseignants**, aussi bien dans Excel que dans CSV : la colonne `courriel` peut être absente ou ses cellules vides. Le matricule (élève) et le numéro d’employé (enseignant) restent obligatoires et servent à distinguer les personnes, y compris pour plusieurs affectations d’un enseignant. Un courriel renseigné doit être valide. Omettre le courriel d’un dossier existant conserve son adresse ; aucune invitation ne peut être envoyée pour un nouveau dossier sans courriel.

Champs disponibles dans le modèle élèves :

| Informations | Colonnes | Conditions |
| --- | --- | --- |
| Identité et inscription | `prénom`, `nom`, `matricule`, `classe` | Obligatoires ; classe existante de l’année sélectionnée |
| Coordonnées et état civil | `courriel`, `téléphone`, `date_naissance`, `sexe` | Facultatifs ; dates `AAAA-MM-JJ` |
| Parent ou tuteur | `parent_prénom`, `parent_nom`, `parent_courriel`, `parent_téléphone`, `lien_parenté` | Prénom et nom obligatoires uniquement si un parent est renseigné ; courriel et téléphone facultatifs |
| Frais et versements | `type_frais`, `frais`, `échéance`, `paiement`, `date_paiement`, `mode_paiement` | Voir les règles de reprise ci-dessous |

**Correction de l’import des parents :** l’ancienne version exigeait le courriel du parent et déclenchait son ajout uniquement lorsque `parent_courriel` était rempli. Désormais, un parent sans courriel est importé avec son nom, son téléphone éventuel et son lien de parenté (`Père`, `Mère`, `Tuteur`…). Un parent par ligne est pris en charge ; les autres parents se rattachent depuis la fiche élève. L’aperçu affiche ces informations avant confirmation. Un dossier sans parent affiche explicitement « Non renseigné ».

Dans la colonne **Parents**, les actions utilisent des icônes avec infobulles : enveloppe pour le courriel, téléphone pour appeler, envoi pour transmettre le lien d’activation d’un compte non activé, ou clé pour demander la réinitialisation du mot de passe d’un compte déjà activé. L’activation dépend de la confirmation du courriel et du choix d’un mot de passe. Sans courriel, seules les actions compatibles avec les coordonnées disponibles sont proposées. Le résultat d’une demande de réinitialisation conserve le message générique du service, sans promettre la livraison du courriel.

Pour compléter des élèves déjà importés, reprendre leurs mêmes matricules, noms et classes et ajouter les colonnes parent. Laisser les colonnes financières vides pour ne pas reprendre les frais. Une relance retrouve le parent déjà lié par son courriel ou, sans courriel, par son nom sur cet élève ; elle ne le crée pas à nouveau. Les identités et coordonnées des comptes existants sont conservées et se modifient depuis leur fiche. Sans courriel, les parents de deux élèves différents ne sont pas fusionnés sur la seule base d’un nom ou d’un téléphone. Les champs personnels d’un élève ou enseignant existant restent conservés : l’import complète les rattachements et les montants, pas leur profil. Il ne reconstitue pas les notes, présences, documents, emplois du temps ou historiques détaillés de versements.

Exemple d’un élève avec son parent sans courriel (adapter la classe et le matricule) :

```csv
prénom;nom;matricule;classe;date_naissance;sexe;parent_prénom;parent_nom;parent_téléphone;lien_parenté
Awa;Traoré;M001;CP1;2014-03-02;F;Mamadou;Traoré;+22670000000;Père
```

Dans **Élèves par classe**, le modèle CSV/Excel accepte les colonnes facultatives `type_frais`, `frais`, `échéance`, `paiement`, `date_paiement` et `mode_paiement`. Téléchargez le nouveau modèle depuis l’écran. Le type de frais doit déjà être configuré pour l’établissement et le niveau. Les montants sont exprimés en FCFA ; les dates suivent le format `AAAA-MM-JJ`.

Le matricule retrouve un élève déjà inscrit dans la classe de l’année sélectionnée, après vérification de son nom et de son courriel. Une ligne peut créer ou mettre à jour un frais, identifié par **élève + année + type de frais + échéance**. Pour plusieurs types de frais, utilisez des imports successifs. Les colonnes financières vides n’ajoutent aucun frais. Les colonnes de la liste affichent les frais nets actifs, les paiements reçus et le reste à payer selon la même logique que la fiche élève, y compris les reports et les paiements de frais annulés.

**`paiement` représente le total déjà payé pour ce frais**, et non un nouveau versement. Exemple : frais de 50 000 FCFA, déjà payé 20 000 FCFA, puis fichier indiquant 30 000 FCFA payés : seul un complément de 10 000 FCFA est enregistré avec un reçu. Réimporter le même total n’ajoute pas de paiement. Une cellule `paiement` vide conserve les versements existants ; `0` n’est accepté que si rien n’a déjà été payé. L’import ne supprime aucun versement et ne modifie pas les frais annulés ou bénéficiant d’une réduction : utiliser la fiche élève pour ces corrections. Un complément exige sa date réelle et son mode (`CASH`, `MOBILE_MONEY`, `BANK_TRANSFER` ou `CARD`). Ne regroupez que des versements dont la saisie à cette date et avec ce mode est justifiée ; sinon, saisissez l’historique détaillé dans la fiche.

Exemple de colonnes à ajouter au fichier élèves :

```csv
type_frais;frais;échéance;paiement;date_paiement;mode_paiement
Scolarité;50000;2026-10-01;20000;2026-09-15;CASH
```

Dans **Enseignants par classe**, les colonnes `salaire` et `mois_salaire` (`AAAA-MM`) reprennent un salaire mensuel fixe. Les colonnes `paiement`, `date_paiement` et `mode_paiement` reprennent le total versé pour ce mois. Le numéro d’employé, le nom et le courriel identifient un enseignant existant. Pour plusieurs affectations, répéter les mêmes informations financières. Les rémunérations horaires ou selon présence restent gérées dans la fiche enseignant. Les taux déjà protégés par des paiements ultérieurs ne sont pas remplacés.

La reprise de salaire prépare uniquement le salaire de l’enseignant concerné et utilise le circuit **Salaires → Dépenses à payer → versement**, avec contrôle du net après retenues et sans doubler les paiements. La liste affiche le net, les versements et le solde pour le mois choisi, toutes classes confondues dans cet établissement.

Les imports restent traités ligne par ligne : les lignes réussies sont conservées et les erreurs sont affichées pour reprise. L’élève, ses montants et le rattachement de son parent sont enregistrés dans une même transaction : une erreur empêche l’enregistrement de toute cette ligne. Le frontend vérifie aussi que la réponse du serveur confirme le parent demandé. Pour les enseignants, la création/affectation précède la transaction de reprise du salaire : si cette dernière échoue, le profil reste disponible pour une nouvelle tentative. Les montants nécessitent les droits **Frais et paiements** ou **Salaires** en plus des accès aux listes. Redémarrer le backend et recharger le frontend après mise à jour ; aucune migration de base n’est nécessaire.

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

## Années scolaires, employés et clôture

Le sélecteur de l'en-tête permet de choisir un établissement et une année scolaire. Une année clôturée reste consultable ; ses données scolaires sont protégées contre les modifications.

Dans l'espace propriétaire/admin :
- **Créer un employé** crée un enseignant avant son affectation aux classes, ou un autre membre du personnel avec ses accès autorisés. Une invitation permet de choisir son mot de passe.
- **Inscriptions** gère les admissions ; les passages sont dans **Clôturer année**.
- **Clôturer année** prépare la nouvelle année et copie les classes, les affectations et la configuration des périodes. L'admin valide les passages, redoublements et départs. Les notes ne sont jamais copiées.
- La finalisation exige de traiter les signalements en attente et les décisions des élèves. Les soldes de caisse et de banque sont saisis puis confirmés. Les impayés sont reportés comme références aux factures originales, sans créer de seconde dette.
- La clôture et l'activation de la nouvelle année sont réalisées dans une même transaction. Les interfaces abonnées aux changements de l'établissement actualisent leurs données ; la sélection par défaut suit la nouvelle année courante.

Redémarrer le backend pour appliquer **V34** avec Flyway, puis recharger le frontend. Avant une clôture réelle, vérifier les décisions et les soldes : la clôture protège les archives et ne propose pas d'annulation.

## Administration de la plateforme

- Le rôle global SUPER_ADMIN est enregistré dans user_platform_roles et ne dépend pas d'une école. L'inscription publique ne permet pas d'obtenir ce rôle.
- L'accueil /admin affiche les établissements et leurs propriétaires : recherche, filtre par statut, pagination, activation/désactivation avec confirmation et contact par courriel ou téléphone. La liste est actualisée toutes les 20 secondes.
- Les changements de statut sont enregistrés dans school_status_events avec l'auteur et l'heure. Les dates des anciens établissements actifs sont initialisées avec leur date de création ; les changements ultérieurs utilisent l'heure réelle.
- Un mot de passe temporaire impose un changement dans Mon profil avant l'accès aux API métier. Le changement invalide les sessions : se reconnecter avec le nouveau mot de passe.
- La migration V36 ajoute ces données. Le compte personnel SUPER_ADMIN est provisionné uniquement dans la base locale, sans identifiants dans les migrations ou le dépôt.

## Export des données de l'établissement

Le propriétaire dispose du module **Exporter mes données** (/proprietaire/export). Les accès délégués au personnel ne donnent pas droit à cet export complet.

- Le téléchargement Excel produit un véritable classeur .xlsx nommé donnees_etablissement_YYYY-MM-DD_HH-mm-ss.xlsx, avec une feuille par domaine, des colonnes en français, des filtres, une légende et un sommaire.
- Toutes les années sont incluses : identités et coordonnées, liens parent-enfant, employés, inscriptions, passages, affectations, notes, bulletins, présences, signalements, emplois du temps, paie, frais, factures, paiements, impayés reportés, soldes d'ouverture, dépenses, budgets, publications, rendez-vous et conversations accessibles à l'établissement.
- L'option ZIP contient ce classeur et les fichiers joints du portail et de ces conversations. Les feuilles d'inventaire indiquent leurs chemins dans l'archive. Les documents enregistrés comme liens externes sont seulement référencés : leur contenu n'est pas téléchargé.
- L'export utilise un instantané REPEATABLE_READ. Il ignore le choix d'année de l'interface et reste accessible au propriétaire d'un établissement suspendu ou archivé. Aucun mot de passe ni jeton d'authentification n'est exporté ; les échanges privés hors des conversations accessibles à l'établissement sont exclus.
- Les factures restent les dettes originales : ne pas additionner une deuxième fois les lignes d'impayés reportés. Les soldes actuels et au report sont distingués.
- Les textes trop longs pour Excel sont conservés intégralement dans Textes longs. Les grandes feuilles sont réparties lorsque la limite de lignes Excel est atteinte. Les valeurs utilisateur sont écrites comme texte, sans formules exécutables.
- Le fichier temporaire est transmis puis supprimé du serveur. Aucune nouvelle migration n'est nécessaire. Redémarrer le backend et recharger le frontend après installation.

API : GET /api/owner/export/schools, GET /api/owner/export/schools/{schoolId}/excel et GET /api/owner/export/schools/{schoolId}/archive. Les deux téléchargements vérifient le propriétaire réel de l'école (ou SUPER_ADMIN). L'export est une copie de consultation, sans import automatique ni récupération des données déjà supprimées.

Le format du classeur suit [SpreadsheetML / Open XML](https://learn.microsoft.com/en-us/office/open-xml/spreadsheet/structure-of-a-spreadsheetml-document) ; la génération utilise les API Java standard ZIP/XML sans dépendance Excel supplémentaire.

## Inscription d’un propriétaire depuis l’accueil

Le bouton **Ajouter mon établissement** ouvre `/ajouter-etablissement`. Le propriétaire confirme son courriel avant de préparer une école. Un compte existant peut être réutilisé sans modifier ses dossiers.

L’assistant crée un **DRAFT**, puis soumet l’établissement en **PENDING_APPROVAL** après configuration d’au moins une année scolaire et une classe. Seul le SUPER_ADMIN peut ensuite valider et activer l’établissement depuis son tableau des écoles. Les inscriptions publiques restent réservées aux écoles **ACTIVE**. Les collaborateurs n’accèdent pas aux écoles en préparation ou en attente.

La migration Flyway **V37** ajoute le compte propriétaire, la date de soumission et la synchronisation du statut. Elle s’applique au redémarrage du backend. Les migrations déjà appliquées restent inchangées.

API : `POST /api/auth/register-owner` (public), `POST /api/users/me/owner-account` (compte confirmé), finalisation via l’API existante des écoles.

## Destinataire des courriels en local

Avec le profil `local` seul, tous les courriels (confirmation, invitation, réinitialisation et rappels) sont redirigés par EmailService vers `boubacar.sawadogo02@gmail.com`. La variable `MAIL_TEST_RECIPIENT` permet de choisir une autre boîte ; une valeur vide désactive la redirection. Cette configuration est définie uniquement dans `application-local.yml`. Dans les autres environnements, même si cette variable est présente, les destinataires réels sont utilisés. La redirection est également désactivée si un autre profil est activé avec `local`.

Les adresses des comptes et les jetons restent associés aux utilisateurs de test : seule la destination SMTP change. Les logs indiquent le destinataire original et la boîte de test, sans afficher les liens ou les jetons. Redémarrer le backend après modification.

## Validation visible des formulaires

Les formulaires utilisent une directive commune : erreurs sous les champs et résumé lors de la soumission, focus du premier champ invalide, aucun appel de création tant que les formats et limites ne sont pas respectés. Courriels, téléphones (6 à 15 chiffres, séparateurs usuels), nombres, dates, champs obligatoires et limites déclarées sont contrôlés. Les boutons de validation restent accessibles pour expliquer les erreurs.

La création d’un établissement charge le véritable propriétaire au lieu de reconstruire un utilisateur avec un identifiant seul : les comptes versionnés doivent conserver leur état JPA. Les autres associations vers User utilisent également une référence JPA gérée. Aucune migration supplémentaire n’est nécessaire.

## Isolation des enseignants lors des affectations

Le sélecteur « Affecter un enseignant existant » est limité aux établissements du propriétaire de l’école cible. Le backend interroge ces établissements par propriétaire ; il ne charge plus le catalogue global. Cette limite s’applique aussi au SUPER_ADMIN dans ce sélecteur. Le personnel délégué ne voit que les enseignants de son établissement courant.

Le backend refuse l’affectation d’un enseignant d’un autre propriétaire, même si son identifiant est fourni directement. Une réutilisation entre deux écoles du même propriétaire conserve les profils par établissement. Le frontend filtre également les propositions selon les établissements accessibles au compte. Aucune migration de données n’est requise.


### Graphiques complémentaires de l’accueil

Le tableau de bord expose `classReceivables` (droit FINANCE) et `weeklyAttendance` (droit STUDENTS), pour l’année scolaire sélectionnée. Le recouvrement répartit les frais actifs nets de réduction entre encaissé plafonné aux frais, solde échu et solde non échu à la date du jour. Les trop-perçus sont séparés ; les factures annulées sont exclues. Les frais sont rattachés à la dernière inscription de l’année, ou au groupe sans classe. Il s’agit d’un état des créances, distinct des flux mensuels de trésorerie.

Les présences couvrent au maximum huit semaines, limitées aux dates de l’année et à aujourd’hui. Le dernier statut par élève, classe et jour est retenu. Un non-renseigné correspond à un élève inscrit à la date de l’appel, encore dans les effectifs actifs/terminés/diplômés, sans statut lors d’un appel commencé dans sa classe. Sans calendrier complet ni historique des sorties, aucun appel manquant n’est déduit et une semaine vide reste « Sans données ». Les totaux représentent des journées-élèves par classe, pas des élèves uniques. Les détails sont accessibles au clavier et sur mobile.


### Caisse et banque à l’accueil

La carte caisse utilise le solde de clôture mensuel du brouillard existant (mois actuel, ou dernier mois si la caisse est clôturée), sous le droit CASH_BOOK. Une caisse non ouverte ou un chargement en erreur ne sont pas présentés comme un solde nul.

La carte banque (droit FINANCE) indique toujours **Solde non vérifié** : report BANK de l’année sélectionnée + paiements scolaires par BANK_TRANSFER reçus dans la période − dépenses et paiements enseignants par BANK_TRANSFER. Les liaisons de paiements enseignants ne sont pas additionnées aux versements. Les flux effectifs incluent les paiements de factures annulées, comme la trésorerie mensuelle. La période est limitée à aujourd’hui et à la fin de l’année scolaire. Les cartes et paiements mobiles sont exclus faute de preuve de leur versement sur un compte bancaire. Le report absent est signalé explicitement ; cette estimation ne constitue pas un relevé ni un rapprochement bancaire.


### Emplois du temps des classes

Dans **Vie scolaire et familles → Emplois du temps des classes**, choisir l’établissement, la classe et une date de la semaine à consulter, puis **Imprimer / PDF**. La feuille A4 paysage présente les sept jours, les horaires et les enseignants. Aucun planning parallèle n’est saisi : les créneaux de la fiche enseignant sont la source. Les dates de validité, inclusives, sont vérifiées pour chaque occurrence de la semaine, dans les limites de l’année sélectionnée. Les chevauchements entre enseignants dans une classe sont signalés. Les matières affichées sont les affectations actives enseignant/classe, car le créneau ne porte pas de matière unique ; les vacances et annulations restent dans le calendrier. L’accès est protégé par le module CALENDAR, pour le propriétaire, les administrateurs et le personnel autorisé.


### Indicateurs de messages non lus

Le résumé de la boîte établissement et l’accueil utilisent la même requête de comptage que la visibilité de la messagerie : conversations personnelles du compte dans l’établissement et boîte partagée accessible. Le participant personnel est prioritaire lorsqu’une conversation possède aussi un participant établissement. Chaque message est compté une seule fois, les messages envoyés par le compte sont exclus et les conversations privées des autres comptes ne sont pas incluses. Le compteur de l’accueil n’inclut plus l’ancienne table messages, absente de la messagerie actuelle.

Les badges enseignant, parent et élève continuent d’utiliser leur participation personnelle. Les indicateurs sont actualisés après lecture/envoi réussi, au retour sur la fenêtre et toutes les vingt secondes. Les mêmes règles s’appliquent au personnel disposant du module MESSAGES ; en intervention administrateur dans un établissement, le badge utilise ce contexte. Une erreur réseau conserve le dernier compteur connu jusqu’à la prochaine actualisation.


Les cartes de l’emploi du temps utilisent une couleur de fond stable selon le libellé de la matière (ou du groupe de matières associé au créneau), avec une légende et un texte sombre contrasté. Les matières non renseignées restent grises. La version imprimée conserve le fond des cartes. Dans « Créer un employé », les actions de préparation ont un fond bleu, le retrait un fond rouge et la validation un fond vert, avec un contour visible au clavier.


### Espace enseignant et appel de classe

Les quatorze modules métier sont accessibles depuis la navigation et l’accueil enseignant, y compris Messages, Calendrier scolaire, Discipline, Bibliothèque et Devoirs de maison. Le module de présence est nommé « Présences et retards ». Pour la classe et la date choisies, les lignes sans présence enregistrée sont préremplies à Présent ; les statuts existants et les modifications en cours sont conservés. Rien n’est écrit à l’ouverture de la liste. Corriger les exceptions puis utiliser « Enregistrer l’appel de la classe » ; la recherche ne limite pas les élèves enregistrés. Le bouton de chaque ligne reste disponible.

`PUT /api/me/teacher/classes/{classId}/attendance` reçoit la date et les lignes nouvelles ou modifiées. Le serveur vérifie l’affectation enseignant, les inscriptions, les doublons, les dates et les statuts attendus avant toute écriture. Une erreur annule la transaction ; une présence déjà modifiée depuis le chargement demande une actualisation. Les appels non validés restent absents des statistiques.

Graphiques proposés : évolution hebdomadaire des présences/absences/retards des classes affectées, répartition des notes par évaluation et suivi des devoirs remis/corrigés. Les semaines sans appel et les notes non saisies doivent rester distinctes des absences et des zéros.


### Graphiques de l’accueil enseignant

Le module est renommé **Présences et retards**, juste après **Mes classes** dans le menu Enseignement et en tête de l’accueil. Les graphiques de `GET /api/me/teacher/dashboard` suivent la classe choisie et le contexte établissement/année sélectionné. Présences : huit semaines au maximum, bornées par l’année et aujourd’hui, dernier statut par élève/jour ; une semaine vide reste sans saisie. Notes : évaluations des seules matières affectées à l’enseignant, barèmes ramenés sur 20, notes absentes et invalides exclues, vrais zéros comptés. Devoirs : uniquement ses publications, couples élève/devoir sur inscriptions actives, ciblage individuel respecté, statuts À faire/Remis/Corrigés et échéances dépassées. Les contrôles d’accès précèdent les calculs. Actualisation manuelle et toutes les trente secondes ; détails hebdomadaires accessibles au clavier et affichage mobile.


### Ma fiche employé — enseignant

Le module **Ma fiche employé** est accessible dans le menu Enseignement et à l’accueil enseignant. Il présente en lecture seule les données de la fiche administrative : identité professionnelle, matricule, date d’embauche, spécialité, matières, tarif du mois, heures prévues/travaillées/absentes/supplémentaires, montants calculés, retenues prises en compte, versements et créneaux datés. Le choix de l’établissement et du mois est un filtre de consultation. Les calculs utilisent le même service que la fiche administrative, sans simuler des droits d’administration.

Les nouvelles routes `GET /api/me/teacher/employee-files` et `GET .../{teacherId}?month=YYYY-MM` utilisent le compte authentifié et refusent tout dossier d’un autre compte, même dans la même école. Aucune route de modification de contrat, de tarif, de planning ou de paiement n’est ajoutée.

Le formulaire « Demander une action à l’administration » utilise `POST .../{teacherId}/requests` pour créer une conversation privée avec le propriétaire responsable de l’établissement. Le destinataire est fixé côté serveur et la demande ne modifie pas le dossier ; les réponses se consultent dans Messages. Un enseignant sans classe affectée peut consulter sa fiche et écrire à son responsable. L’envoi est désactivé si aucun responsable actif et approuvé distinct du demandeur n’est disponible, ou si l’établissement est inactif.

### Évaluations, devoirs de maison et ressources de cours

- Enseignant : « Notes » devient « Évaluations et notes ». La route existante reste inchangée.
- « Devoirs de maison » réunit publication (consignes, fichiers, date de remise) et suivi par élève (À faire, Remis, Corrigé, retour du professeur). Le professeur ne modifie que le suivi de ses propres devoirs, sur une année ouverte. Les parents et élèves consultent ce suivi sans le modifier.
- « Cours et ressources » réutilise les documents partagés par classe : supports, exercices libres et corrigés sans date de remise. Le titre permet de préciser la matière et le chapitre. Un exercice à remettre se publie dans Devoirs de maison.
- Ces deux pages sont accessibles dans les espaces enseignant, parent et élève. Les anciennes routes personnelles `suivi-devoirs` redirigent vers `devoirs`. Le suivi administratif reste disponible côté établissement.
- Les PDF disposent d’un aperçu en boîte de dialogue et du téléchargement. La lecture passe par l’API authentifiée et ses contrôles d’accès ; aucun document public n’est créé. Si le navigateur mobile ne sait pas afficher le PDF, le téléchargement reste disponible.
- Les lectures de publications et fichiers sous `/api/me/parent-portal/students/{studentId}` et du suivi `/api/school-life/students/{studentId}?module=HOMEWORK` acceptent l’élève pour sa propre fiche ou un parent autorisé. Les actions réservées aux parents restent protégées par `requireGuardedChild`.
- Aucune migration de données : publications et suivis existants conservés. La réception signifie la consultation dans l’espace personnel ; aucun envoi automatique par courriel ou WhatsApp n’est ajouté.

### Outils quotidiens de l’enseignant

La migration V54 ajoute le cahier de textes, les notes privées, l’accompagnement et l’historique des demandes d’indisponibilité. Au démarrage du backend, Flyway l’applique automatiquement.

- **Aujourd’hui**, à l’accueil : cours effectifs à la date du jour dans le contexte sélectionné, devoirs échus restant à vérifier/corriger, évaluations passées avec des notes à saisir et rappels personnels arrivés à échéance. Un zéro réel est une note saisie ; une note manquante ne devient pas un zéro. Actualisation manuelle.
- **Cahier de textes**, volet dépliable dans Cours et ressources : matière affectée au professeur, séance réalisée, contenu, objectifs et prochaine séance. Privé par défaut ; partage volontaire de toute la séance avec la classe. Les PDF et exercices restent dans le formulaire de ressources existant.
- **Élèves à accompagner**, volet de Mes classes : repères indicatifs (trois absences hors absences excusées sur les trente derniers jours, devoirs personnels non remis après échéance ou moyenne indicative sous 10/20 dans ses propres évaluations). Moyenne simple normalisée sur 20, distincte de la moyenne officielle pondérée du bulletin. Le professeur peut également choisir un autre élève de sa classe, noter une action privée, prévoir un bilan et terminer le suivi. Le message destiné à la famille est un champ séparé et n’est publié que sur choix explicite ; la note privée n’est jamais incluse dans la réponse familiale.
- **Carnet personnel** : accessible depuis Aujourd’hui, sans nouvelle rubrique principale du menu. Notes modifiables, supprimables après confirmation, rappels datés et état À faire/Terminé, strictement limités au compte enseignant.
- **Disponibilités et absences**, volet de Mon emploi du temps : dates et heures, motif et demande éventuelle de remplacement, avec sélection de sa propre fiche employé. Le destinataire administratif est choisi par le serveur. La demande et la conversation sont enregistrées dans la même transaction. Le statut affiché est « Demande transmise » ; la réponse se consulte dans Messages. Aucun changement automatique des créneaux ni de la paie.

Les séances et messages d’accompagnement partagés sont consultables dans Cours et ressources des espaces parent et élève via `GET /api/me/students/{studentId}/learning`. L’accès exige sa propre fiche ou un lien parent autorisé dans un établissement actif. Les familles ne disposent d’aucune route d’écriture sur ces outils.

API enseignant : `/api/me/teacher/daily/today`, `/daily/notes`, `/daily/availability`, `/classes/{classId}/lessons` et `/classes/{classId}/support`. Les modifications de séances et d’accompagnement exigent une classe affectée et une année courante ouverte. Les notes sont liées au compte ; les demandes restent liées à sa propre fiche employé.

### Accueil parent et format des montants

L’accueil parent propose un suivi par enfant dans le contexte établissement/année sélectionné. Le nouveau GET `/api/me/parent/dashboard?studentId=...` exige un lien parent/tuteur actif, puis vérifie le contexte avant de lire les données. Les graphiques présentent les moyennes officielles des bulletins publiés par période et par matière, les huit dernières semaines d’appels effectivement renseignés et les devoirs À faire/Remis/Corrigés. Un résultat non publié ou sans note reste absent, tandis qu’un vrai zéro est conservé. Les appels non saisis ne deviennent ni des présences ni des absences. Les dates et élèves ciblés par les publications sont respectés.

Les points à suivre et les listes récapitulent les absences, retards, devoirs échus non remis, messages d’accompagnement partagés, prochaines évaluations sur quatorze jours, annonces et événements. Les liens vers les modules conservent l’enfant choisi. L’accès Accompagnement affiche uniquement les informations volontairement partagées par les professeurs ; leurs observations privées ne sont pas exposées. Les modules scolaires, vie scolaire, communication et démarches sont regroupés à l’accueil. Une erreur de chargement n’est pas affichée comme un compteur nul.

Le format global Angular utilise désormais `fr-FR` avec les données locales françaises. Les montants affichent des espaces insécables entre milliers et une virgule décimale selon la précision demandée par chaque écran (exemple : 5 000 FCFA). Les valeurs numériques stockées, saisies et calculées restent inchangées. Les champs numériques et les reçus disposaient déjà de leur format français.

### Espace élève : suivi et organisation

L’accueil élève présente les cours du jour, devoirs à préparer, évaluations à venir, annonces, calendrier et rappels personnels. Les graphiques affichent les moyennes officielles publiées, les résultats par matière, les présences enregistrées et les statuts des devoirs renseignés par les enseignants. Une note non publiée reste masquée et une présence non saisie ne devient pas une absence.

Les modules Mes présences et retards, Annonces, Mon accompagnement, Mon dossier élève (lecture seule) et Mon organisation complètent les accès aux cours, notes, devoirs, emploi du temps, bibliothèque, discipline, démarches, messages et rendez-vous. Les rappels sont privés au compte de l’élève et ne modifient pas le statut des devoirs de classe.

API : GET /api/me/student/profiles et /api/me/student/dashboard?studentId=… ; CRUD des rappels sous /api/me/student/profiles/{studentId}/tasks. Ces routes exigent le dossier propre de l’utilisateur ; le lien parent ne suffit pas. Les lectures de vie scolaire autorisent aussi l’élève pour son propre dossier ; les écritures administratives restent réservées à leurs rôles. Redémarrer le BFF pour appliquer Flyway V55 (student_study_tasks).

### Brouillard de banque

Le module /proprietaire/banque apparaît après le brouillard de caisse. Il dispose du droit délégué BANK_BOOK, attribuable depuis Personnel et accès. Les propriétaires et administrateurs conservent leur accès aux établissements autorisés. Redémarrer le BFF pour appliquer la migration Flyway V56.

Ouvrir le registre avec une date et le solde avant les premières opérations de cette date (découvert autorisé). Les règlements scolaires, dépenses et salaires enseignants par BANK_TRANSFER sont repris une seule fois ; les dépenses liées aux salaires administratifs sont reprises via expenses. Les paiements sans mode connu, carte ou mobile money sont exclus jusqu’à la saisie du dépôt bancaire correspondant. Les autres entrées et sorties sont saisies avec date, montant, libellé et référence ; les requêtes identiques réessayées ne créent pas de doublon.

Le report mensuel inclut les mois vides et changements d’année. Le solde et les exports sont explicitement « avant rapprochement bancaire ». Ce module ne réalise pas de rapprochement avec un relevé et n’applique pas les clôtures de caisse. Seuls les mouvements manuels peuvent être supprimés ici ; les opérations automatiques se corrigent dans leur module d’origine. Une saisie manuelle ne crée pas de recette ou charge budgétaire. Pour un transfert caisse/banque, enregistrer le mouvement correspondant dans chacun des deux registres.

API : GET /api/owner/bank-book/schools/{schoolId}?month=YYYY-MM ; POST /open ; POST /entries ; DELETE /entries/{id}. Tests PostgreSQL sur base jetable locale : BankBookIntegrationTest avec -DbankBookTestUrl=jdbc:postgresql://127.0.0.1:PORT/bank_book_test.

### Paie automatique et fiscalité des factures

La préparation mensuelle des salaires calcule l’IUTS et la TPA, groupe les contrats d’un même employé dans l’établissement et alimente les obligations PAYROLL sans double création. Les calculs validés et les paramètres fiscaux enregistrés sont réutilisés ; les anciennes saisies manuelles explicites restent prioritaires. Sans paramètres, le régime salarié est utilisé avec le brut comme base, zéro charge familiale et aucune exonération TPA. Ces hypothèses sont visibles et modifiables dans Salaires. Les versements antérieurs ne sont pas modifiés ; un paiement excédant le net recalculé doit être régularisé. Pour les salaires existants, le bouton Calculer et synchroniser IUTS / TPA appelle POST /api/owner/taxes/schools/{schoolId}/synchronize-payroll?year=YYYY. Les consultations GET ne créent aucune écriture fiscale.

Les dépenses peuvent inclure une facture (numéro, date, IFU fournisseur), HT, TTC et plusieurs taxes. Les taxes INCLUDED font déjà partie du TTC ; les taxes WITHHELD sont déduites du paiement fournisseur. La dépense et la caisse/banque conservent le net effectivement payé. L’action distincte Créer les reversements fiscaux inscrit les retenues dans Impôts et taxes, avec l’origine EXPENSE et le lien à la facture ; elle n’effectue aucun paiement. L’utilisateur doit disposer des droits Dépenses et Impôts. Les références et échéances ne sont pas inventées. Une facture liée à une obligation active ne peut pas être modifiée ou supprimée ; annuler explicitement l’obligation non réglée avant correction. Les taxes facturées ne sont pas ajoutées une seconde fois aux reversements.

Redémarrer le BFF pour Flyway V57. Les règles de paie réutilisent BurkinaPayrollCalculator (BF-CGI-2026-v1). Pour les factures, les taux ne sont pas imposés automatiquement : les conditions de retenue diffèrent selon le fournisseur, l’opération et le statut fiscal de l’établissement. Sources DGI : https://dgi.bf/verification/CGI et loi de finances 2026 publiée au Journal officiel (https://dgi.bf/wp-content/uploads/2026/01/JOURNAL-OFFICIEL-N%C2%B001-SPECIAL-LOI-DE-FINANCES-2026.pdf).

### Catalogues de matières et imports

Les nouveaux établissements SECONDAIRE (et les matières communes du type MIXTE) reçoivent les 15 matières générales avec codes et catégories : FRA, MAT, ANG, ALL, ESP, HGE, PHI, SVT, PCH, ECM, EPS, INF, SES, APL, MUS. Les coefficients initiaux restent à 1, modifiables par matière et par classe ; une catégorie n’est pas un coefficient.

Dans Configuration de l’école → Académique → Matières → Afficher les catalogues, les établissements existants peuvent ajouter le secondaire général et les cinq filières techniques (gestion-commerce, informatique, électricité, mécanique, bâtiment). Le catalogue complète seulement les codes et catégories manquants, conserve les noms, codes personnalisés et coefficients, et signale les conflits sans écraser les données. Une application répétée ne crée pas de doublon. Les matières techniques ne sont pas imposées à tous les lycées : l’établissement choisit ses filières.

RDM désigne une matière partagée entre mécanique et bâtiment. SEC désigne Secrétariat ; SCT désigne Sécurité de chantier pour éviter un même code pour deux matières différentes. Les imports enseignants acceptent dans la colonne matière le nom ou le code, sans distinction de casse ni d’accents. Les codes ambigus sont rejetés plutôt que rattachés arbitrairement.

GET /api/subjects/catalog?schoolId=… et POST /api/subjects/catalog/{packId}?schoolId=… exigent le droit MANAGEMENT dans l’établissement. Les catalogues sont des modèles modifiables, pas un programme officiel. Redémarrer le BFF pour appliquer Flyway V58 (subjects.category).

### Taxes lors de la création des dépenses à payer

Dans Dépenses & budget → Dépenses à payer → Ajouter une dépense (ou Ajouter une charge fixe), le bouton « Ajouter la TVA / une taxe » permet de saisir les taxes facturées et comprises dans le montant TTC. Le HT est calculé par différence ; le total des taxes ne peut pas dépasser le TTC. Les références de facture, sa date et l’IFU fournisseur sont facultatifs. Une charge fixe conserve ses taxes et son IFU sans répéter le numéro ou la date d’une facture pour les mois suivants.

Les taxes sont conservées dans les détails et réparties entre les versements partiels, avec régularisation des arrondis au dernier paiement. Les paiements et registres conservent leur montant TTC effectivement payé, sans ajout de taxe une seconde fois. Cette saisie ne crée pas de reversement fiscal. Les dépenses déjà payées disposent toujours de leur formulaire de facture et de retenues distinct. Redémarrer le BFF pour appliquer Flyway V59 (expense_taxes).

Validation PostgreSQL sur base jetable locale : PayableTaxesIntegrationTest avec -DpayableTaxesTestUrl=jdbc:postgresql://127.0.0.1:PORT/payable_taxes_test.

### État de TVA fournisseurs

Impôts et taxes distingue les obligations de reversement de la TVA facturée à l’établissement par ses fournisseurs. L’état reprend les dépenses à payer MANUAL/FIXED non annulées et les factures directement enregistrées dans les dépenses payées ; les paiements gérés issus des échéances sont exclus pour éviter le double comptage. Seules les lignes TVA incluse sont reprises, pas les retenues WITHHELD. Les modèles de charges fixes ne sont pas comptés avant préparation d’une échéance.

La date de facture détermine l’année et la période ; à défaut, la date d’échéance ou du paiement est utilisée avec une mention explicite. Le détail et l’export CSV suivent les filtres mois/trimestre/année. Le total concerne la TVA facturée, pas nécessairement déjà payée ou déductible. Il ne crée aucune obligation fiscale et ne modifie aucun registre. La TVA collectée par l’établissement et les reports de crédits ne sont pas calculés par cet état : aucun net à déclarer n’est déduit des seuls achats. Un excédent de TVA déductible sur la TVA collectée est un crédit de TVA, pas une promesse de remboursement automatique. Redémarrer le BFF pour exposer supplierVat dans l’aperçu fiscal ; aucune nouvelle migration.
