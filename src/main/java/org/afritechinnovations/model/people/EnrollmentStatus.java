package org.afritechinnovations.model.people;

public enum EnrollmentStatus {
    ACTIVE,
    /** Année scolaire clôturée : la décision de fin d'année est renseignée. */
    COMPLETED,
    TRANSFERRED,
    GRADUATED,
    DROPPED
}