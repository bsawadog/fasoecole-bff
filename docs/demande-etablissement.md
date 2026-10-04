# Demande simplifiée d’établissement

La demande comprend le nom, le type, l’adresse, les effectifs prévus
(élèves / étudiants, classes, enseignants), ainsi qu’un téléphone et un courriel
facultatifs. Les effectifs sont des estimations, pas les effectifs réellement
inscrits. Au moins une classe est prévue ; élèves et enseignants peuvent être zéro.

`POST /api/schools/requests` enregistre la demande puis la soumet dans une seule
transaction. Le propriétaire est imposé à partir du compte connecté. La demande
reste `PENDING_APPROVAL` jusqu’à activation par le SUPER_ADMIN. La soumission ne
nécessite plus d’année scolaire ni de classe enregistrée.

Les brouillons existants peuvent être repris, complétés avec les estimations
puis soumis. Le SUPER_ADMIN voit l’adresse et les estimations dans sa liste.
Après approbation, le propriétaire peut commencer la configuration réelle via
Gestion de l’école. Les modules restent présentés en aperçu avant approbation.
L’espace propriétaire actualise le statut toutes les 20 secondes.

La migration additive `V39__school_request_estimates.sql` s’applique au prochain
démarrage du backend. Les établissements existants conservent leurs données et
leur statut. Ne modifiez pas les migrations déjà appliquées.

## Cloisonnement des matricules

Les listes de matricules et la génération des matricules sont limitées à
l’établissement. Les listes d’inscriptions filtrent également les relations
incohérentes entre établissement de la classe et établissement de l’élève.
La modification d’un élève refuse un identifiant appartenant à un autre
établissement que la classe demandée.

Le frontend efface le contexte d’établissement et d’année à la déconnexion,
ignore les anciennes réponses lors d’un changement de classe / établissement,
et désactive l’historique de saisie du navigateur pour les champs matricules.
`autocomplete="off"` reste une indication au navigateur et n’efface pas les
valeurs déjà enregistrées dans son historique.

## Contrôles d’approbation proposés

Le contrôle automatique a refusé l’application en masse des contrôles de phase
aux modules opérationnels. La proposition est conservée dans
`approbation-modules-proposition.patch` pour autorisation avant application.
Elle appelle `SchoolApprovalPolicy` après les contrôles existants d’accès
dans les modules scolaires, élèves, enseignants et personnel.
Les contrôles existants de propriétaire ne sont pas remplacés.

Tant que cette proposition n’est pas appliquée, le frontend affiche l’attente
d’approbation, mais les contrôles serveur supplémentaires de phase ne sont pas
tous branchés. Le cloisonnement existant par propriétaire reste nécessaire et
les corrections spécifiques aux élèves ont été appliquées.
