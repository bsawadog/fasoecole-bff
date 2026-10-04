# Incidents et assistance

La migration V41 ajoute les incidents, leur image facultative et leur historique. Redémarrer le backend pour appliquer cette migration ; elle ne modifie pas B39 ni les migrations déjà appliquées.

Le propriétaire utilise **Incidents** pour sélectionner son établissement, renseigner le titre, la page concernée et les étapes du problème. Une capture PNG/JPEG de 2 Mo maximum peut être jointe. Les établissements en attente d'approbation peuvent également déclarer un incident.

Le SUPER_ADMIN consulte **Incidents**, prend en charge une demande puis décrit la correction avant de demander un nouveau test. Le propriétaire peut signaler que le problème persiste ou fermer l'incident. Le SUPER_ADMIN peut également fermer ou rouvrir un incident. Les commentaires et changements sont conservés avec l'identité de leur auteur.

Les changements génèrent des notifications internes persistantes. Le module actualise sa liste et le détail toutes les dix secondes ; aucune notification par courriel n'est ajoutée.

Le bouton **Ouvrir l'espace** dans la liste des établissements et dans le détail d'un incident ouvre les modules propriétaires pour le SUPER_ADMIN. Le sélecteur permet de changer d'établissement et d'année scolaire. Aucun accord préalable ou délai de session d'assistance n'est imposé. L'utilisateur conserve son identité SUPER_ADMIN ; le propriétaire n'est pas impersonné. L'entrée par ces boutons est inscrite dans audit_logs. L'historique des incidents est également audité ; ceci ne constitue pas un audit de chaque lecture de données.

Les API d'incidents et de captures contrôlent l'appartenance à l'établissement côté serveur. Un propriétaire n'accède pas aux incidents des autres propriétaires. Les captures sont stockées en base et incluses dans les sauvegardes : prévoir leur rétention et la capacité de stockage en exploitation.
