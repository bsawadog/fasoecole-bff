package org.afritechinnovations.service.academic;

import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolType;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.SubjectRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchoolAcademicDefaultsTest {
    @ParameterizedTest @EnumSource(SchoolType.class)
    void initializesAnEditableCurrentYearAndDistinctSubjectsForEverySchoolType(SchoolType type) {
        var years = mock(AcademicYearRepository.class);
        var subjects = mock(SubjectRepository.class);
        var service = new SchoolAcademicDefaults(years, subjects);
        service.setClock(Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC));
        var school = School.builder().id(1L).type(type).build();
        service.initialize(school);
        var year = ArgumentCaptor.forClass(AcademicYear.class);
        verify(years).save(year.capture());
        assertEquals("2026-2027", year.getValue().getLabel());
        assertTrue(year.getValue().getIsCurrent()); assertFalse(year.getValue().isClosed());
        assertSame(school, year.getValue().getSchool());
        verify(subjects).saveAll(argThat(items -> {
            List<Subject> list = (List<Subject>) items;
            return !list.isEmpty() && list.stream().allMatch(item -> item.getSchool() == school && item.getCoefficient().signum() > 0)
                    && list.stream().map(Subject::getName).distinct().count() == list.size();
        }));
    }

    @ParameterizedTest @CsvSource({"2026-09-30T12:00:00Z,2025-2026", "2026-10-01T12:00:00Z,2026-2027", "2027-01-01T12:00:00Z,2026-2027"})
    void picksTheAcademicYearAcrossCalendarYearBoundaries(String instant, String label) {
        var years = mock(AcademicYearRepository.class);
        var service = new SchoolAcademicDefaults(years, mock(SubjectRepository.class));
        service.setClock(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        service.initialize(School.builder().type(SchoolType.PRIMAIRE).build());
        verify(years).save(argThat(year -> label.equals(year.getLabel())
                && !LocalDate.ofInstant(Instant.parse(instant), ZoneOffset.UTC).isBefore(year.getStartDate())
                && !LocalDate.ofInstant(Instant.parse(instant), ZoneOffset.UTC).isAfter(year.getEndDate())));
    }
}
