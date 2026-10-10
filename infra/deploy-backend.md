# Déploiement manuel du backend : dev ou prod

Dans Actions > Deploy backend > Run workflow, choisir l'environnement :

| Input | Branche par défaut | Stack plateforme | Stack service |
|---|---|---|---|
| dev | develop | fasoecole-dev-platform | fasoecole-dev-backend |
| prod | main | fasoecole-prod-platform | fasoecole-prod-backend |

Le fichier workflow conserve son nom deploy-dev.yml mais son titre est
Deploy backend. Aucun push ne déclenche ce déploiement.
La concurrence est séparée par environnement ; le profil Spring, les rôles
de tâche IAM, les exports CloudFormation, le dépôt ECR et le service ECS
suivent tous l'environnement sélectionné.

Créer les environnements GitHub dev et prod. Définir dans CHAQUE environnement :

- AWS_ROLE_ARN : rôle GitHub propre à l'environnement.
- AWS_CLOUDFORMATION_ROLE_ARN : rôle d'exécution CloudFormation correspondant.
- SMTP_HOST et MAIL_FROM : configuration de messagerie.
- DEPLOY_BRANCH : facultatif ; remplace develop pour dev ou main pour prod.

Restreindre les branches de chaque environnement GitHub en cohérence avec
DEPLOY_BRANCH. Une branche incorrecte ou un rôle absent arrête le workflow
avant l'authentification AWS.
La confiance OIDC doit utiliser le sujet de l'environnement correspondant :
repo:bsawadog/fasoecole-bff:environment:dev ou
repo:bsawadog/fasoecole-bff:environment:prod.
Ne pas réutiliser le rôle dev pour prod : le bootstrap dev n'autorise que
les ressources et stacks dont le nom est fasoecole-dev-*.

## Prérequis de production

Le choix prod ne crée pas automatiquement une plateforme de production.
Les templates platform.json et bootstrap.json fournis dans infra/dev sont
réservés au dev. Préparer séparément la plateforme et les rôles de production,
éventuellement dans un compte AWS distinct, avant d'exécuter ce choix.

La stack plateforme cible doit exposer RepositoryUri et FrontendUrl.
Elle doit exporter sous le préfixe fasoecole-<environment>- :
Cluster, Public0, Public1, TaskSecurity, Target, DatabaseHost,
DatabaseSecret, JwtSecret, SmtpSecret, FrontendUrl et LogGroup.
Le dépôt ECR est fasoecole-<environment>-bff, le cluster
fasoecole-<environment> et le service fasoecole-<environment>-bff.
Les secrets PostgreSQL ont les champs username/password ; le secret SMTP
possède également username/password. Le secret JWT est une chaîne Base64.
Le frontend CloudFront doit transmettre /api/* à l'API sans cache.

Le template de service partagé est infra/dev/backend.json pour conserver
la compatibilité avec l'installation dev. TargetEnvironment sélectionne le
profil Spring et tous les imports. Le profil prod valide TLS avec le bundle
RDS Paris inclus dans l'image, expose uniquement health sans détails et
désactive Swagger ainsi que le contrôle SMTP dans le health check.

Le service conserve le dimensionnement initial : une tâche, 0,5 vCPU / 2 Go.
Le choix prod ne suffit donc pas à définir la capacité, la haute disponibilité
ou la stratégie de sauvegarde nécessaires à une exploitation réelle.
Il faut dimensionner ces éléments avant la mise en production.
