# FasoEcole dev — AWS Paris

Architecture : Angular sur S3 privé + CloudFront ; API Spring Boot sur ALB
et ECS Fargate ; PostgreSQL 16 RDS Single-AZ dans deux sous-réseaux privés.
Une seule tâche : 0,5 vCPU / 2 Go. Pas de NAT Gateway.
La tâche a une IPv4 publique mais accepte seulement le port 8080 depuis l'ALB.
L'ALB accepte seulement les adresses d'origine CloudFront : port 80 sans domaine,
ou port 443 avec un domaine et un certificat ACM.
CloudFront transmet /api/* sans cache, avec Authorization, cookies, paramètres
et en-têtes métier ; les routes Angular sont réécrites séparément vers index.html.
Le domaine CloudFront par défaut fournit HTTPS au navigateur.

Budget indicatif : 80–100 USD/mois avec peu de trafic, hors taxes, crédits,
sauvegardes supplémentaires et éventuels crédits CPU RDS. Un déploiement roulant
peut exécuter temporairement deux tâches. Ce dimensionnement doit être mesuré.

## Fichiers et ordre

1. bootstrap.json : identité OIDC GitHub + rôles de déploiement.
2. platform.json : réseau, base, secrets, ECR, ALB et frontend.
3. backend.json : tâche et service ECS ; requiert une image déjà publiée.
   Ce template accepte TargetEnvironment=dev ou prod ; platform.json et
   bootstrap.json de ce dossier restent réservés au dev.

Noms des stacks obligatoires : fasoecole-dev-bootstrap, fasoecole-dev-platform,
fasoecole-dev-backend. Les exports et permissions S3 utilisent ces noms.
Un seul environnement dev de cette application par compte/région.

## Permissions du bootstrap

Le rôle GitHub backend fait les changements CloudFormation des stacks dev,
publie dans fasoecole-dev-bff et lit l'état ECS. Il passe uniquement le rôle
fasoecole-dev-cloudformation à CloudFormation.
Le rôle GitHub frontend lit les sorties de stack, écrit dans le bucket dev
et invalide CloudFront. Il ne peut pas déployer l'infrastructure.
Les deux identités OIDC sont limitées aux dépôts configurés et à l'environnement
GitHub dev. Protéger cet environnement pour autoriser uniquement develop.

Le rôle d'exécution CloudFormation crée/modifie/supprime réseau, RDS, ALB, ECS,
ECR, secrets, logs, S3 et CloudFront. Plusieurs actions ont Resource="*" car
les ressources sont créées avec des identifiants générés. Ce rôle reste puissant :
ses permissions d'infrastructure ne sont pas toutes limitées à des ressources
dev ; réserver ce bootstrap à un compte AWS de développement.
Les rôles IAM de tâche sont limités au préfixe fasoecole-dev-task.
Aucune clé AWS permanente n'est stockée dans GitHub.

## Prérequis

- Compte AWS autorisé à créer ces ressources.
- Deux dépôts : bsawadog/fasoecole-bff et bsawadog/fasoecole-spa.
- Branche develop ; changer aussi les conditions des workflows si elle change.
- Facultatif : domaine API contrôlé, par exemple api.dev.votre-domaine,
  et certificat public ACM ISSUED à Paris couvrant ce domaine.
  Le fournisseur DNS peut être externe à Route 53.
- AWS CLI v2 et GitHub Actions activé.

Sans domaine, laisser les deux champs du workflow infrastructure vides.
CloudFront utilise directement le DNS AWS de l'ALB en HTTP. Les visiteurs
utilisent toujours HTTPS via FrontendUrl, y compris pour /api/*.
Ce mode est destiné aux tests : le trajet CloudFront vers l'ALB n'est pas chiffré.
Avec un domaine, fournir les deux champs. Le certificat n'est pas créé
automatiquement : sa validation nécessite un accès au DNS du domaine.

## Première installation (PowerShell)

Depuis la racine du dépôt backend :

```powershell
aws sts get-caller-identity
aws cloudformation deploy --region eu-west-3 --stack-name fasoecole-dev-bootstrap --template-file infra/dev/bootstrap.json --capabilities CAPABILITY_NAMED_IAM
aws cloudformation describe-stacks --region eu-west-3 --stack-name fasoecole-dev-bootstrap --query 'Stacks[0].Outputs'
```

Si le compte dispose déjà du fournisseur token.actions.githubusercontent.com,
passer ExistingOidcProviderArn au bootstrap pour le réutiliser.

Créer un environnement GitHub dev dans CHAQUE dépôt. Restreindre les branches
de déploiement à develop. Définir les variables :

| Variable | Backend | Frontend |
|---|---|---|
| AWS_ROLE_ARN | Sortie BackendRoleArn | Sortie FrontendRoleArn |
| AWS_CLOUDFORMATION_ROLE_ARN | Sortie CloudFormationRoleArn | Inutile |
| CLOUDFRONT_PREFIX_LIST_ID | pl-75b1541c à Paris, vérifier ci-dessous | Inutile |
| SMTP_HOST | Facultatif, smtp.gmail.com par défaut | Inutile |
| MAIL_FROM | Adresse d'expéditeur autorisée | Inutile |

```powershell
aws ec2 describe-managed-prefix-lists --region eu-west-3 --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing --query 'PrefixLists[].PrefixListId'
```

Publier les fichiers dans les dépôts GitHub. workflow_dispatch exige que le
workflow existe sur la branche par défaut ; ensuite sélectionner develop.
Lancer "Deploy infrastructure dev" sur develop. Sans domaine personnel,
laisser api_domain et api_certificate_arn vides. Avec un domaine,
renseigner le domaine API et l'ARN ACM.
Le premier déploiement RDS/CloudFront peut durer plusieurs minutes.
Avec un domaine uniquement : récupérer ApiDnsTarget dans les sorties et créer
le CNAME du domaine API vers ce nom ALB. Attendre sa résolution avant de lancer
le backend. Sans domaine, aucune configuration DNS n'est nécessaire.

Dans Secrets Manager, remplir le secret SmtpSecret avec username et password
sans mettre ces valeurs dans Git ou les variables GitHub. Pour Gmail, utiliser
un mot de passe d'application ; vérifier les règles de votre fournisseur.
RDS gère son mot de passe maître ; JWT_SECRET est généré automatiquement.
Une rotation des secrets exige un nouveau déploiement des tâches ECS.

Lancer manuellement "Deploy backend" depuis GitHub Actions sur develop,
en sélectionnant l'input environment=dev,
puis "Deploy frontend dev". Ces workflows font les tests avant publication.
Le backend se déploie uniquement manuellement ; le frontend se relance aussi
sur les pushes develop.
Les workflows CI existants restent indépendants.
Pour le choix prod, voir ../deploy-backend.md. L'infrastructure et les rôles
de production doivent être créés séparément avant de lancer ce choix.

## Base et comptes

Flyway exécute B39 sur une base vide, puis V40 et les migrations suivantes.
Ne pas importer le dump manuellement. V40 provisionne le SUPER_ADMIN configuré
dans le code avec un mot de passe aléatoire non communiqué. Vérifier le parcours
de définition du mot de passe et le SMTP pour accéder au compte.
La connexion RDS valide TLS et le nom du serveur avec le bundle AWS Paris
embarqué dans l'image. Mettre à jour ce bundle lors des rotations de CA AWS.
ddl-auto=validate reste actif ; les migrations échouées empêchent le démarrage.

## Vérification et exploitation

- FrontendUrl : accueil et accès direct à une route Angular.
- /api/users/me sans JWT doit renvoyer 401, jamais index.html.
- Le groupe cible ALB vérifie /actuator/health (sans détails publics).
- Logs : /ecs/fasoecole-dev-bff, rétention 7 jours.
- Vérifier inscription, connexion, appels authentifiés, courriels et exports.
- Mesurer les temps depuis le Burkina et les métriques CPU/mémoire/RDS.
- En cas d'échec : sorties CloudFormation et événements ECS ; le circuit breaker
  peut revenir à un déploiement précédent. Une migration déjà appliquée n'est
  pas annulée : utiliser des migrations compatibles avec la version précédente.
- Le dev n'est pas hautement disponible : une tâche et RDS Single-AZ.

Validation locale : mvn package dans le backend, npm test -- --watch=false et
npm run build:dev dans le frontend. Le workflow Validate AWS infrastructure
contrôle les schémas CloudFormation à chaque modification infra.
infra/dev/validate.py contrôle aussi les workflows quand les deux dépôts sont
présents côte à côte (installer cfn-lint). Après mvn package, exécuter
infra/dev/smoke.ps1 depuis le backend avec Docker : le script crée une base
jetable, vérifie B39/V40, health et l'accès anonyme refusé, puis nettoie ses
conteneurs, son réseau et son image de test.

## Arrêt définitif après passage en production

Exporter les données utiles ou conserver un snapshot, puis supprimer dans cet
ordre la stack backend, la stack platform et enfin le bootstrap.
La suppression platform crée un snapshot RDS. Le bucket frontend et le dépôt
ECR sont conservés pour éviter la perte d'artefacts.
Les snapshots, objets S3 et images ECR conservés restent facturés : les supprimer
séparément lorsque leur conservation n'est plus nécessaire.
Ne pas supprimer le fournisseur OIDC s'il a été réutilisé pour d'autres dépôts.

Réduire DesiredCount à zéro coupe le calcul Fargate mais pas l'ALB ni RDS.
Un arrêt RDS est temporaire et AWS redémarre l'instance après sept jours.
