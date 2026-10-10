# Tests d’intégration de messagerie

## Backend

`MessagingHttpIntegrationTest` démarre toute l’application Spring Boot sur un port aléatoire. Les requêtes HTTP utilisent de vrais JWT, la sécurité et les services de l’application, ainsi qu’une vraie base PostgreSQL initialisée par Flyway.

Les quatre scénarios vérifient :

- un enseignant envoie à un parent, qui lit et répond ;
- un utilisateur anonyme ou d’une autre école ne peut pas lire la conversation ;
- un destinataire interdit fait échouer tout l’envoi sans création partielle ;
- une pièce jointe est conservée dans PostgreSQL et accessible uniquement aux participants.

Depuis le dossier du BFF, avec Docker et Maven disponibles :

```powershell
./scripts/test-school-life.ps1
```

Le script lance aussi les tests de vie scolaire existants. Il crée puis supprime son propre conteneur PostgreSQL temporaire. Sans la propriété `schoolLifeTestUrl`, les tests PostgreSQL sont ignorés par `mvn test`. Le workflow CI existant fournit cette propriété et exécute ces tests.

## Frontend

Dans le dépôt SPA, `messages.integration.spec.ts` assemble le vrai composant enseignant, son formulaire et le vrai service HTTP. Seules les réponses HTTP sont simulées : ce n’est pas un test de navigateur connecté au backend.

Les deux scénarios vérifient la sélection de classe et des parents, le contenu de la requête d’envoi et l’ouverture de la conversation, puis l’affichage d’un refus du serveur avec conservation du brouillon.

Depuis le dossier du frontend :

```powershell
npm test -- --watch=false
```

Le workflow CI frontend existant les inclut automatiquement. Ces scénarios ajoutent une couverture fonctionnelle de la messagerie ; ils ne mesurent pas un pourcentage de couverture du code.

## Mesurer la couverture

Backend, avec Docker : `./scripts/test-school-life.ps1 -Coverage`. Cette option exécute toute la suite, y compris les intégrations, avec JaCoCo et supprime les anciennes données de couverture avant la mesure. Le rapport HTML est `target/site/jacoco/index.html`. `mvn test` produit également un rapport, mais ignore les intégrations PostgreSQL si leur URL n’est pas fournie.

Frontend : `npm run test:coverage`. Le rapport HTML est `coverage/fasoecole-front/index.html`. Les 88 fichiers TypeScript de `src/app` sont inclus, même ceux sans tests ; les fichiers de tests sont exclus.

Mesure du 7 octobre 2026 : backend, 264 tests réussis, 51,03 % des lignes et 40,18 % des branches ; frontend, 98 tests réussis, 40,90 % des lignes et 33,10 % des branches. Ces chiffres couvrent toute la suite, pas uniquement les nouveaux tests de messagerie. Ils évolueront avec le code et les tests.
