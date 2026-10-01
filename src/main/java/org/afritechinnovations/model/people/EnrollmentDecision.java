package org.afritechinnovations.model.people;

/** Décision de fin d'année prise pour une inscription. */
public enum EnrollmentDecision {
    /** Admis en classe supérieure. */
    PROMOTED,
    /** Redouble la classe. */
    REPEATED,
    /** Fin de cycle / diplômé : quitte l'établissement par le haut. */
    GRADUATED,
    /** Quitte l'établissement (départ, transfert, abandon). */
    LEFT
}
