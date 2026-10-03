package org.afritechinnovations.service.academic;

import org.afritechinnovations.model.academic.AcademicYear;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** A viewing context, never a permission or a change to the school's current year. */
public final class SelectedAcademicYear {
    private SelectedAcademicYear() {}
    public static Long id(Long schoolId) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)) return null;
        String school = a.getRequest().getHeader("X-Academic-School");
        String year = a.getRequest().getHeader("X-Academic-Year");
        if (school == null || year == null || !school.equals(String.valueOf(schoolId))) return null;
        try { long id = Long.parseLong(year); if(id<=0) throw new NumberFormatException(); return id; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Année scolaire invalide."); }
    }
    public static boolean matches(AcademicYear year) {
        Long id = id(year.getSchool().getId());
        if (RequestContextHolder.getRequestAttributes() == null) return true;
        return schoolMatches(year.getSchool().getId()) && (id == null ? Boolean.TRUE.equals(year.getIsCurrent()) : id.equals(year.getId()));
    }
    public static boolean schoolMatches(Long schoolId) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)) return true;
        String school = a.getRequest().getHeader("X-Academic-School");
        return school == null || school.equals(String.valueOf(schoolId));
    }
    public static boolean dateMatches(Long schoolId, java.time.LocalDate date) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)) return true;
        if (!(a.getRequest().getAttribute("selectedAcademicYear") instanceof AcademicYear year)) return true;
        return schoolId.equals(year.getSchool().getId()) && !date.isBefore(year.getStartDate()) && !date.isAfter(year.getEndDate());
    }
    public static java.time.LocalDate viewDate(Long schoolId,java.time.LocalDate today) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)) return today;
        if (!(a.getRequest().getAttribute("selectedAcademicYear") instanceof AcademicYear year) || !schoolId.equals(year.getSchool().getId())) return today;
        if (today.isBefore(year.getStartDate())) return year.getStartDate();
        return today.isAfter(year.getEndDate()) ? year.getEndDate() : today;
    }
}
