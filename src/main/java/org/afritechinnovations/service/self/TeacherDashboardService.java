package org.afritechinnovations.service.self;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.service.academic.SelectedAcademicYear;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class TeacherDashboardService {
 private final JdbcTemplate jdbc;private final TeacherSpaceService teacher;
 public record Week(LocalDate start,LocalDate end,long present,long absent,long late,long excused) {}
 public record Evaluation(Long id,String title,String subject,LocalDate date) {}
 public record Bucket(String label,long count) {}
 public record Marks(Long evaluationId,List<Evaluation> evaluations,List<Bucket> buckets,long count,BigDecimal average) {}
 public record Homework(long todo,long submitted,long corrected,long overdue) {}
 public record Dashboard(List<SelfServiceDto.TeacherClass> classes,Long classId,String yearLabel,LocalDate asOf,List<Week> weeks,Marks marks,Homework homework) {}
 private record Bounds(LocalDate start,LocalDate end) {}
 public Dashboard overview(Long user,Long classId,Long evaluationId){return overview(user,classId,evaluationId,LocalDate.now());}
 Dashboard overview(Long user,Long classId,Long evaluationId,LocalDate today){
  var choices=teacher.classes(user).stream().filter(c->SelectedAcademicYear.schoolMatches(c.schoolId()) && (SelectedAcademicYear.id(c.schoolId())==null || SelectedAcademicYear.id(c.schoolId()).equals(c.academicYearId()))).toList();
  var selected=classId==null?choices.stream().filter(SelfServiceDto.TeacherClass::currentYear).findFirst().orElse(choices.isEmpty()?null:choices.getFirst()):choices.stream().filter(c->c.classId().equals(classId)).findFirst().orElseThrow(()->new AccessDeniedException("Cette classe ne vous est pas affectée dans le contexte sélectionné."));
  if(selected==null)return new Dashboard(choices,null,null,today,List.of(),new Marks(null,List.of(),List.of(),0,null),new Homework(0,0,0,0));
  Long id=selected.classId();
  var bounds=jdbc.query("SELECT y.start_date,y.end_date FROM classes c JOIN academic_years y ON y.id=c.academic_year_id WHERE c.id=? AND c.school_id=?",(r,n)->new Bounds(r.getDate(1).toLocalDate(),r.getDate(2).toLocalDate()),id,selected.schoolId()).getFirst();
  LocalDate end=today.isBefore(bounds.end())?today:bounds.end();LocalDate monday=end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(7);LocalDate from=monday.isAfter(bounds.start())?monday:bounds.start();
  List<Week> weeks=end.isBefore(bounds.start())?List.of():jdbc.query("""
   WITH bounds AS(SELECT ?::date first_day,?::date last_day), recorded AS(
    SELECT DISTINCT ON(a.student_id,a.attendance_date) a.attendance_date,a.status FROM attendances a JOIN students s ON s.id=a.student_id
    WHERE a.class_id=? AND s.school_id=? AND a.attendance_date BETWEEN ? AND ? ORDER BY a.student_id,a.attendance_date,a.id DESC
   ), totals AS(SELECT date_trunc('week',attendance_date)::date week,COUNT(*) FILTER(WHERE status='PRESENT') present,COUNT(*) FILTER(WHERE status='ABSENT') absent,COUNT(*) FILTER(WHERE status='LATE') late,COUNT(*) FILTER(WHERE status='EXCUSED') excused FROM recorded GROUP BY 1),
   weeks AS(SELECT generate_series(date_trunc('week',first_day),date_trunc('week',last_day),interval '1 week')::date week FROM bounds)
   SELECT greatest(w.week,b.first_day) first_day,least(w.week+6,b.last_day) last_day,coalesce(t.present,0) present,coalesce(t.absent,0) absent,coalesce(t.late,0) late,coalesce(t.excused,0) excused FROM weeks w CROSS JOIN bounds b LEFT JOIN totals t ON t.week=w.week ORDER BY w.week
   """,(r,n)->new Week(r.getDate("first_day").toLocalDate(),r.getDate("last_day").toLocalDate(),r.getLong("present"),r.getLong("absent"),r.getLong("late"),r.getLong("excused")),from,end,id,selected.schoolId(),from,end);
  var evaluations=jdbc.query("""
   SELECT e.id,e.title,s.name subject,e.eval_date FROM evaluations e JOIN class_subject_teacher a ON a.id=e.class_subject_teacher_id JOIN teachers t ON t.id=a.teacher_id JOIN subjects s ON s.id=a.subject_id JOIN classes c ON c.id=a.class_id JOIN academic_years y ON y.id=c.academic_year_id
   WHERE a.class_id=? AND t.user_id=? AND t.school_id=c.school_id AND (a.active=TRUE OR y.closed_at IS NOT NULL) ORDER BY e.eval_date DESC,e.id DESC
   """,(r,n)->new Evaluation(r.getLong("id"),r.getString("title"),r.getString("subject"),r.getDate("eval_date").toLocalDate()),id,user);
  Long chosen=evaluationId==null?(evaluations.isEmpty()?null:evaluations.getFirst().id()):evaluations.stream().filter(e->e.id().equals(evaluationId)).findFirst().orElseThrow(()->new AccessDeniedException("Cette évaluation ne relève pas de vos matières dans cette classe.")).id();
  Marks marks=new Marks(null,evaluations,List.of(),0,null);
  if(chosen!=null)marks=jdbc.query("""
   WITH latest AS(SELECT DISTINCT ON(g.student_id) g.value,g.max_value FROM grades g JOIN evaluations e ON e.id=g.evaluation_id JOIN students s ON s.id=g.student_id WHERE g.evaluation_id=? AND g.class_subject_teacher_id=e.class_subject_teacher_id AND s.school_id=? ORDER BY g.student_id,g.id DESC),
   scores AS(SELECT value*20/max_value score FROM latest WHERE max_value>0 AND value BETWEEN 0 AND max_value)
   SELECT COUNT(*) total,ROUND(AVG(score),2) average,COUNT(*) FILTER(WHERE score<5) b0,COUNT(*) FILTER(WHERE score>=5 AND score<10) b1,COUNT(*) FILTER(WHERE score>=10 AND score<12) b2,COUNT(*) FILTER(WHERE score>=12 AND score<16) b3,COUNT(*) FILTER(WHERE score>=16) b4 FROM scores
   """,(r,n)->new Marks(chosen,evaluations,List.of(new Bucket("0 à < 5",r.getLong("b0")),new Bucket("5 à < 10",r.getLong("b1")),new Bucket("10 à < 12",r.getLong("b2")),new Bucket("12 à < 16",r.getLong("b3")),new Bucket("16 à 20",r.getLong("b4"))),r.getLong("total"),r.getBigDecimal("average")),chosen,selected.schoolId()).getFirst();
  var homework=jdbc.query("""
   WITH assigned AS(SELECT DISTINCT p.id,s.id student_id,p.due_date,coalesce(h.status,'TO_DO') status FROM parent_portal_posts p JOIN classes c ON c.id=p.class_id JOIN student_enrollments e ON e.class_id=c.id AND e.academic_year_id=c.academic_year_id AND e.status='ACTIVE' JOIN students s ON s.id=e.student_id AND s.school_id=p.school_id LEFT JOIN homework_progress h ON h.post_id=p.id AND h.student_id=s.id AND h.school_id=p.school_id
    WHERE p.class_id=? AND p.school_id=? AND p.author_id=? AND p.kind='HOMEWORK' AND (p.student_id IS NULL OR p.student_id=s.id))
   SELECT COUNT(*) FILTER(WHERE status='TO_DO') todo,COUNT(*) FILTER(WHERE status='SUBMITTED') submitted,COUNT(*) FILTER(WHERE status='CORRECTED') corrected,COUNT(*) FILTER(WHERE status='TO_DO' AND due_date<?) overdue FROM assigned
   """,(r,n)->new Homework(r.getLong("todo"),r.getLong("submitted"),r.getLong("corrected"),r.getLong("overdue")),id,selected.schoolId(),user,today).getFirst();
  return new Dashboard(choices,id,selected.academicYearLabel(),today,weeks,marks,homework);
 }
}
