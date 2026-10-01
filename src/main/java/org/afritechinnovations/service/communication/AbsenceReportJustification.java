package org.afritechinnovations.service.communication;

/** Texte de justification reporté sur une présence couverte par une absence signalée et acceptée. */
public final class AbsenceReportJustification {

    private static final String PREFIX = "Signalée par le parent : ";
    private static final int MAX_LENGTH = 255;

    private AbsenceReportJustification() {
    }

    public static String of(String reason) {
        String text = PREFIX + (reason == null ? "" : reason.trim());
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 1) + "…";
    }
}