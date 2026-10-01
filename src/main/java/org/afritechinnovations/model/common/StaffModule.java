package org.afritechinnovations.model.common;

/** Modules de l'espace propriétaire qui peuvent être délégués à un membre du personnel. */
public enum StaffModule {
    DASHBOARD("Tableau de bord"),
    MANAGEMENT("Gestion de l'école"),
    STUDENTS("Élèves & dossiers"),
    TEACHERS("Enseignants & paie"),
    FINANCE("Frais & paiements"),
    EXPENSES("Dépenses & budget"),
    GRADES("Notes & bulletins"),
    ENROLLMENT("Inscriptions & passage");

    private final String label;

    StaffModule(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
