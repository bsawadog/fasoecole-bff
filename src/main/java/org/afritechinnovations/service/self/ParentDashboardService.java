package org.afritechinnovations.service.self;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.service.academic.SelectedAcademicYear;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ParentDashboardService {
 private final JdbcTemplate jdbc;
 private final FamilySpaceService family;
 public record Attendance(long present,long absent,long late,long excused) { public long recorded(){return present+absent+late+excused;} }
 public record Week(LocalDate start,LocalDate end,Attendance attendance) {}
 public record Period(Long id,String name,boolean published,BigDecimal average) {}
 public record Subject(String name,BigDecimal average) {}
 public record Homework(long todo,long submitted,long corrected,long overdue) {}
 public record DueHomework(Long id,String title,LocalDate dueOn,boolean overdue) {}
 public record Notice(Long id,String title,LocalDateTime createdAt) {}
 public record Upcoming(String title,String subject,LocalDate date) {}
 public record Event(String title,LocalDate startsOn,LocalDate endsOn) {}
 public record Overview(SelfServiceDto.StudentOverview child,LocalDate asOf,LocalDate firstDay,LocalDate lastDay,
   Attendance attendance,List<Week> weeks,List<Period> periods,String subjectPeriod,List<Subject> subjects,
   Homework homework,List<DueHomework> dueHomework,List<Upcoming> evaluations,List<Notice> announcements,List<Event> events,long sharedSupport) {}
 private record Bounds(LocalDate start,LocalDate end) {}
 private record Daily(LocalDate date,String status) {}
 private record Work(Long id,String title,LocalDate dueOn,String status) {}
 public Overview overview(Long user,Long studentId){return overview(user,studentId,LocalDate.now());}
 Overview overview(Long user,Long studentId,LocalDate today){
  return overview(user,studentId,today,false);
 }
 public Overview overviewForStudent(Long user,Long studentId,LocalDate today){return overview(user,studentId,today,true);}
 private Overview overview(Long user,Long studentId,LocalDate today,boolean ownStudent){
  var student=ownStudent?family.requireOwnStudent(user,studentId):family.requireGuardedChild(user,studentId);
  if(!SelectedAcademicYear.schoolMatches(student.getSchool().getId()))throw new AccessDeniedException("Choisissez l’établissement de cet enfant.");
  var child=family.student(user,studentId);
  var grades=family.grades(user,studentId);
  var periods=grades.periods().stream().map(p->new Period(p.periodId(),p.periodName(),p.published(),p.published()&&p.bulletin()!=null?p.bulletin().average():null)).toList();
  var published=grades.periods().stream().filter(p->p.published()&&p.bulletin()!=null).max(Comparator.comparing(SelfServiceDto.PeriodGrades::startDate));
  var subjects=published.map(p->p.bulletin().lines().stream().map(line->new Subject(line.subjectName(),line.average())).toList()).orElse(List.of());
  Long classId=child.classId(),schoolId=child.schoolId();
  if(classId==null)return new Overview(child,today,null,null,new Attendance(0,0,0,0),List.of(),periods,published.map(SelfServiceDto.PeriodGrades::periodName).orElse(null),subjects,new Homework(0,0,0,0),List.of(),List.of(),List.of(),List.of(),0);
  var bounds=jdbc.query("SELECT y.start_date,y.end_date FROM classes c JOIN academic_years y ON y.id=c.academic_year_id WHERE c.id=? AND c.school_id=?",(r,n)->new Bounds(r.getObject(1,LocalDate.class),r.getObject(2,LocalDate.class)),classId,schoolId).stream().findFirst().orElseThrow(()->new AccessDeniedException("Classe inaccessible."));
  LocalDate end=today.isBefore(bounds.end())?today:bounds.end();
  var daily=end.isBefore(bounds.start())?List.<Daily>of():jdbc.query("SELECT DISTINCT ON(a.attendance_date) a.attendance_date,a.status FROM attendances a JOIN students s ON s.id=a.student_id WHERE a.student_id=? AND a.class_id=? AND s.school_id=? AND a.attendance_date BETWEEN ? AND ? ORDER BY a.attendance_date,a.id DESC",(r,n)->new Daily(r.getObject(1,LocalDate.class),r.getString(2)),studentId,classId,schoolId,bounds.start(),end);
  var weeks=new ArrayList<Week>();
  if(!end.isBefore(bounds.start())){
   LocalDate monday=end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(7);
   for(LocalDate week=monday;!week.isAfter(end);week=week.plusWeeks(1)){
    LocalDate from=week.isBefore(bounds.start())?bounds.start():week,to=week.plusDays(6).isAfter(end)?end:week.plusDays(6);
    if(to.isBefore(from))continue;
    weeks.add(new Week(from,to,count(daily.stream().filter(d->!d.date().isBefore(from)&&!d.date().isAfter(to)).toList())));
   }
  }
  var work=jdbc.query("SELECT p.id,p.title,p.due_date,coalesce(h.status,'TO_DO') status FROM parent_portal_posts p LEFT JOIN homework_progress h ON h.post_id=p.id AND h.student_id=? AND h.school_id=p.school_id WHERE p.school_id=? AND p.class_id=? AND p.kind='HOMEWORK' AND (p.student_id IS NULL OR p.student_id=?) ORDER BY p.due_date,p.id",(r,n)->new Work(r.getLong("id"),r.getString("title"),r.getObject("due_date",LocalDate.class),r.getString("status")),studentId,schoolId,classId,studentId);
  var homework=new Homework(work.stream().filter(w->w.status().equals("TO_DO")).count(),work.stream().filter(w->w.status().equals("SUBMITTED")).count(),work.stream().filter(w->w.status().equals("CORRECTED")).count(),work.stream().filter(w->w.status().equals("TO_DO")&&w.dueOn()!=null&&w.dueOn().isBefore(today)).count());
  var due=work.stream().filter(w->w.status().equals("TO_DO")).limit(5).map(w->new DueHomework(w.id(),w.title(),w.dueOn(),w.dueOn()!=null&&w.dueOn().isBefore(today))).toList();
  var upcoming=jdbc.query("SELECT e.title,s.name,e.eval_date FROM evaluations e JOIN class_subject_teacher a ON a.id=e.class_subject_teacher_id JOIN subjects s ON s.id=a.subject_id WHERE a.class_id=? AND e.eval_date BETWEEN ? AND ? AND e.eval_date BETWEEN ? AND ? ORDER BY e.eval_date,e.id LIMIT 5",(r,n)->new Upcoming(r.getString(1),r.getString(2),r.getObject(3,LocalDate.class)),classId,today,today.plusDays(14),bounds.start(),bounds.end());
  var notices=jdbc.query("SELECT p.id,p.title,p.created_at FROM parent_portal_posts p WHERE p.school_id=? AND p.kind='ANNOUNCEMENT' AND (p.student_id=? OR (p.student_id IS NULL AND (p.class_id IS NULL OR p.class_id=?))) AND p.created_at::date BETWEEN ? AND ? ORDER BY p.created_at DESC,p.id DESC LIMIT 3",(r,n)->new Notice(r.getLong(1),r.getString(2),r.getObject(3,LocalDateTime.class)),schoolId,studentId,classId,bounds.start(),bounds.end());
  var events=jdbc.query("SELECT title,starts_on,ends_on FROM school_calendar_events WHERE school_id=? AND (class_id IS NULL OR class_id=?) AND ends_on>=? AND starts_on<=? AND ends_on>=? ORDER BY starts_on,id LIMIT 4",(r,n)->new Event(r.getString(1),r.getObject(2,LocalDate.class),r.getObject(3,LocalDate.class)),schoolId,classId,today,bounds.end(),bounds.start());
  Long shared=jdbc.queryForObject("SELECT count(*) FROM teacher_student_support p JOIN classes c ON c.id=p.class_id WHERE p.student_id=? AND p.class_id=? AND c.school_id=? AND p.shared=true AND p.resolved=false",Long.class,studentId,classId,schoolId);
  return new Overview(child,today,bounds.start(),end.isBefore(bounds.start())?null:end,count(daily),weeks,periods,published.map(SelfServiceDto.PeriodGrades::periodName).orElse(null),subjects,homework,due,upcoming,notices,events,shared==null?0:shared);
 }
 private Attendance count(List<Daily> days){return new Attendance(days.stream().filter(d->d.status().equals("PRESENT")).count(),days.stream().filter(d->d.status().equals("ABSENT")).count(),days.stream().filter(d->d.status().equals("LATE")).count(),days.stream().filter(d->d.status().equals("EXCUSED")).count());}
}
