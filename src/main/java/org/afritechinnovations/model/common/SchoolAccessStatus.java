package org.afritechinnovations.model.common;

public enum SchoolAccessStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Accès accordé automatiquement (parent reconnu par son courriel) ; le propriétaire peut le retirer. */
    AUTO_APPROVED,
    /** Accès retiré par le propriétaire : ne sera plus réaccordé automatiquement. */
    REVOKED
}
