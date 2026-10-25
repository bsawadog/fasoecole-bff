package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ClassTimetableService {
    private final JdbcTemplate jdbc;
    public record ClassChoice(Long id,String name) {}
    public record Lesson(Long id,int dayOfWeek,LocalDate date,LocalTime startTime,LocalTime endTime,String teacherName,String subjects) {}
    public record Overview(String schoolName,String yearLabel,LocalDate yearStart,LocalDate yearEnd,Long classId,String className,LocalDate weekStart,List<ClassChoice> classes,List<Lesson> lessons) {}
    private record Year(Long id,String label,LocalDate start,LocalDate end,String school) {}
    public Overview overview(Long school,Long classId,LocalDate reference) {
        Long selected=SelectedAcademicYear.id(school);
        var years=jdbc.query("""
            SELECT y.id,y.label,y.start_date,y.end_date,s.name school_name FROM academic_years y JOIN schools s ON s.id=y.school_id
            WHERE y.school_id=? AND (?::bigint IS NULL AND y.is_current=TRUE OR y.id=?::bigint)
            """,(r,n)->new Year(r.getLong("id"),r.getString("label"),r.getDate("start_date").toLocalDate(),r.getDate("end_date").toLocalDate(),r.getString("school_name")),school,selected,selected);
        if(years.isEmpty())throw new IllegalArgumentException("Aucune année scolaire sélectionnée pour cet établissement.");
        Year year=years.getFirst();
        LocalDate date=reference==null?LocalDate.now():reference;
        if(reference==null)date=date.isBefore(year.start())?year.start():date.isAfter(year.end())?year.end():date;
        if(date.isBefore(year.start())||date.isAfter(year.end()))throw new IllegalArgumentException("Choisissez une date dans l’année scolaire sélectionnée.");
        LocalDate monday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var classes=jdbc.query("SELECT id,name FROM classes WHERE school_id=? AND academic_year_id=? ORDER BY name,id",(r,n)->new ClassChoice(r.getLong("id"),r.getString("name")),school,year.id());
        var chosen=classId==null?classes.stream().findFirst().orElse(null):classes.stream().filter(c->c.id().equals(classId)).findFirst().orElseThrow(()->new IllegalArgumentException("Classe indisponible pour cet établissement et cette année."));
        List<Lesson> lessons=chosen==null?List.of():jdbc.query("""
            SELECT sl.id,sl.day_of_week,sl.start_time,sl.end_time,CONCAT_WS(' ',u.first_name,u.last_name) teacher_name,
              (SELECT string_agg(DISTINCT subject.name,', ' ORDER BY subject.name) FROM class_subject_teacher a JOIN subjects subject ON subject.id=a.subject_id
               WHERE a.class_id=sl.class_id AND a.teacher_id=sl.teacher_id AND a.active=TRUE) subjects
            FROM teacher_schedule_slots sl JOIN teachers t ON t.id=sl.teacher_id JOIN users u ON u.id=t.user_id
            WHERE sl.class_id=? AND t.school_id=? AND (?::date+sl.day_of_week-1) BETWEEN ?::date AND ?::date
              AND sl.effective_from <= (?::date+sl.day_of_week-1)
              AND (sl.effective_to IS NULL OR sl.effective_to >= (?::date+sl.day_of_week-1))
            ORDER BY sl.day_of_week,sl.start_time,sl.end_time,teacher_name,sl.id
            """,(r,n)->new Lesson(r.getLong("id"),r.getInt("day_of_week"),monday.plusDays(r.getInt("day_of_week")-1),r.getTime("start_time").toLocalTime(),r.getTime("end_time").toLocalTime(),r.getString("teacher_name"),r.getString("subjects")),chosen.id(),school,monday,year.start(),year.end(),monday,monday);
        return new Overview(year.school(),year.label(),year.start(),year.end(),chosen==null?null:chosen.id(),chosen==null?null:chosen.name(),monday,classes,lessons);
    }
}
