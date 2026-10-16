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
    ENROLLMENT("Inscriptions & passage"),
    CALENDAR("Calendrier scolaire"),
    DISCIPLINE("Discipline et vie scolaire"),
    LIBRARY("Bibliothèque"),
    HOMEWORK("Suivi des devoirs"),
    REQUESTS("Demandes administratives"),
    PARENT_PORTAL("Portail parents"),
    MESSAGES("Messages"),
    CASH_BOOK("Brouillard de caisse"),
    FINANCIAL_STATEMENTS("États financiers");

    private final String label;

    StaffModule(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
