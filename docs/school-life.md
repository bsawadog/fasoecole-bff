# Modules de vie scolaire

La migration Flyway `V42__school_life_modules.sql` ajoute les tables nécessaires. Elle fonctionne après la baseline B39 et après une base existante à jour. Les modules sont accessibles dans les menus du frontend ; le backend doit être redémarré pour appliquer la migration avant de les utiliser.

| Module | École / personnel avec accès Élèves | Enseignant | Parent / élève |
| --- | --- | --- | --- |
| Calendrier | Créer et retirer un événement pour l'école ou une classe | Lire les événements de sa classe et de l'école | Lire les événements de la classe de l'enfant et de l'école |
| Discipline | Enregistrer, traiter et partager une observation | Enregistrer une observation dans ses classes | Lire seulement les observations partagées concernant son enfant / soi-même |
| Demandes administratives | Répondre et passer une demande en cours, prête à retirer ou refusée | Aucun accès aux demandes | Demander un document, consulter son traitement et annuler sa propre demande en attente |
| Bibliothèque | Ajouter les livres, enregistrer les prêts et retours | Consulter le catalogue et les prêts de sa classe | Consulter le catalogue et les prêts de son enfant / soi-même |
| Suivi des devoirs | Suivre les devoirs des élèves | Marquer ses propres devoirs à faire, remis ou corrigés, avec un retour | Consulter le statut et le retour |

Les inscriptions et réinscriptions existent déjà dans le module Inscriptions : elles ne sont pas dupliquées. La publication des devoirs et leurs pièces jointes restent dans les écrans Devoirs et Portail parents existants.

Le suivi des devoirs correspond à une remise en classe, sans téléversement de copies par les élèves. Les documents administratifs sont préparés par l'école et retirés selon les modalités précisées dans sa réponse ; aucun document officiel n'est généré automatiquement.

Les emprunts sont sérialisés avec un verrou PostgreSQL sur le livre afin que des prêts simultanés ne dépassent pas le stock. Les dates de retour permettent d'afficher les prêts en retard. Les observations sont internes par défaut ; lorsqu'elles sont partagées, la description et la mesure prise sont visibles par la famille.

Les nouvelles données figurent aussi dans l'export de l'établissement. Les modules utilisent les autorisations existantes Élèves / dossiers (`STUDENTS`) pour le personnel délégué, les affectations pour les enseignants et les liens familiaux pour les parents. Les modifications du suivi des devoirs et les nouvelles observations requièrent une inscription active dans l'année courante ouverte.

Le frontend charge les nouveaux écrans à la demande et demande seulement les données du module ouvert. En cas d'échec d'un enregistrement, il conserve la saisie dans l'écran pour permettre de réessayer. Il ne propose pas de synchronisation automatique hors connexion.

## Validation

`mvn test` exécute les tests unitaires et de permissions. Les tests PostgreSQL sont activés uniquement avec une URL locale dédiée, et le script suivant crée puis supprime sa propre base temporaire dans Docker :

```powershell
./scripts/test-school-life.ps1
```

Ces tests appliquent les vraies migrations, puis vérifient les scopes école / classe / enfant, la confidentialité de la discipline, le traitement des demandes, les stocks et les prêts simultanés, et le suivi individuel des devoirs.

Le workflow BFF CI lance également ces tests avec son propre service PostgreSQL temporaire.
