# Salaires et dépenses à payer

La migration V44 ajoute les salaires mensuels du personnel et des enseignants, les charges fixes, les échéances et les versements. Elle conserve les dépenses et les paies déjà enregistrées.

- **Recouvrement → Salaires** : échéances des employés et des enseignants, versements partiels et historique.
- **Dépenses → À payer** : mêmes salaires, charges mensuelles et dépenses ponctuelles, avec les impayés des mois précédents.
- **Dépenses → Paiements enregistrés** : sorties réellement payées. Les échéances ne diminuent pas la trésorerie avant paiement.

L’ouverture du suivi prépare le mois sélectionné. Cette préparation est répétable sans créer deux fois le même salaire ou la même charge. Les charges fixes commencent au mois choisi ; arrêter une charge conserve les échéances déjà préparées. Les échéances manuelles non payées et les charges fixes non payées peuvent être annulées. Une échéance avec versement conserve son historique.

Le salaire du personnel est figé lors de la préparation. Une modification de sa fiche s’applique aux mois préparés ensuite. Les enseignants nouvellement créés avec un salaire positif ont un taux **mensuel fixe**, payable sans emploi du temps. Les taux horaires et mensuels au prorata déjà configurés continuent de fonctionner ; leur montant dû évolue selon le pointage, comme dans la fiche enseignant. Les changements de taux restent datés et ne peuvent réécrire les mois déjà payés.

Les versements enregistrent une sortie de caisse pour le personnel, les charges fixes et les dépenses ponctuelles. La paie enseignant utilise son registre existant : elle n’est pas enregistrée une seconde fois en caisse. Les versements liés ne peuvent être supprimés ou modifiés depuis le journal des dépenses ou la fiche enseignant.

Les droits FINANCE ou EXPENSES donnent accès au suivi dans l’établissement délégué. Les contrôles d’école sont faits avant toute lecture ou opération. Le serveur verrouille l’échéance, relit le solde après le verrou et refuse les dépassements. Une clé de requête unique empêche de comptabiliser deux fois un versement après une interruption réseau.

Les paiements sont des enregistrements comptables : aucun transfert bancaire ou mobile money n’est exécuté par l’application.

## Vérification

Les tests `PayablesHttpIntegrationTest` utilisent le même PostgreSQL temporaire que les autres tests d’intégration, via `-DschoolLifeTestUrl=jdbc:postgresql://127.0.0.1:PORT/school_life_test` (utilisateur postgres et mot de passe disposable-test-only). Ils couvrent la préparation répétée, la paie, le bilan sans double comptage, les versements partiels, la répétition après interruption, les paiements simultanés, les droits et les annulations.
