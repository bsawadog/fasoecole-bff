package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolType;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.SubjectRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

/** Modèles de départ modifiables, sans imposer un programme officiel. */
@Service
@RequiredArgsConstructor
public class SchoolAcademicDefaults {
    private final AcademicYearRepository years;
    private final SubjectRepository subjects;
    private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    void setClock(java.time.Clock clock) { this.clock = clock; }

    @org.springframework.transaction.annotation.Transactional
    public void initialize(School school) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = LocalDate.of(today.getMonthValue() >= 10 ? today.getYear() : today.getYear() - 1, 10, 1);
        years.save(AcademicYear.builder().school(school).label(start.getYear() + "-" + (start.getYear() + 1))
                .startDate(start).endDate(start.plusYears(1).minusDays(1)).isCurrent(true).build());
        subjects.saveAll(subjectNames(school.getType()).stream()
                .map(name -> Subject.builder().school(school).name(name).build()).toList());
    }

    static List<String> subjectNames(SchoolType type) {
        return switch (type) {
            case PRESCOLAIRE -> List.of("Langage et communication", "Pré-mathématiques", "Éveil scientifique",
                    "Éveil social", "Éducation sensorielle", "Graphisme", "Activités artistiques", "Motricité");
            case PRIMAIRE -> List.of("Français", "Mathématiques", "Lecture", "Écriture", "Expression orale",
                    "Expression écrite", "Grammaire", "Conjugaison", "Orthographe", "Vocabulaire",
                    "Sciences d’observation", "Histoire", "Géographie", "Éducation civique et morale",
                    "Éducation physique et sportive", "Dessin", "Chant");
            case SECONDAIRE -> List.of("Français", "Mathématiques", "Anglais", "Histoire-Géographie",
                    "Sciences de la vie et de la Terre", "Physique-Chimie", "Philosophie", "Allemand", "Espagnol",
                    "Éducation civique et morale", "Éducation physique et sportive", "Informatique",
                    "Sciences économiques et sociales", "Arts plastiques", "Musique");
            case MIXTE -> java.util.stream.Stream.concat(subjectNames(SchoolType.PRIMAIRE).stream(),
                    subjectNames(SchoolType.SECONDAIRE).stream()).distinct().toList();
            case UNIVERSITE -> List.of("Méthodologie universitaire", "Français", "Anglais", "Informatique",
                    "Statistiques", "Communication", "Gestion de projet");
            case FORMATION -> List.of("Communication professionnelle", "Français", "Anglais", "Informatique",
                    "Entrepreneuriat", "Gestion de projet", "Hygiène et sécurité au travail", "Pratique professionnelle");
        };
    }
}
