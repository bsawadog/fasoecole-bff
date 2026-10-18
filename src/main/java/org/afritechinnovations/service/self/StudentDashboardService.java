package org.afritechinnovations.service.self;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.afritechinnovations.service.self.ParentDashboardService.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class StudentDashboardService {
 private final ParentDashboardService dashboard;private final FamilySpaceService family;private final StudentStudyService study;private final JdbcTemplate jdbc;
 public record Profile(Long studentId,String fullName,Long schoolId,String schoolName,Long classId,String className,String academicYearLabel) {}
 public record Dashboard(Profile profile,LocalDate asOf,Attendance attendance,List<Week> weeks,List<Period> periods,String subjectPeriod,List<Subject> subjects,Homework homework,List<DueHomework> dueHomework,List<Upcoming> evaluations,List<Notice> announcements,List<Event> events,long sharedSupport,List<SelfServiceDto.ScheduleEntry> courses,List<StudentStudyService.Task> tasks,long pendingTasks,long overdueTasks) {}
 public List<Profile> profiles(Long user){return family.ownStudents(user).stream().map(this::profile).toList();}
 private Profile profile(SelfServiceDto.StudentOverview s){return new Profile(s.studentId(),s.fullName(),s.schoolId(),s.schoolName(),s.classId(),s.className(),s.academicYearLabel());}
 public Dashboard overview(Long user,Long studentId){return overview(user,studentId,LocalDate.now());}
 Dashboard overview(Long user,Long studentId,LocalDate today){
  var d=dashboard.overviewForStudent(user,studentId,today);var child=d.child();
  var courses=child.classId()==null?List.<SelfServiceDto.ScheduleEntry>of():jdbc.query("""
   SELECT slot.id,slot.day_of_week,slot.start_time,slot.end_time,c.id class_id,c.name class_name,k.name school_name,concat(u.first_name,' ',u.last_name) teacher_name,
     coalesce((SELECT string_agg(DISTINCT s.name,', ' ORDER BY s.name) FROM class_subject_teacher a JOIN subjects s ON s.id=a.subject_id WHERE a.class_id=c.id AND a.teacher_id=t.id AND a.active=true),'') subjects
   FROM teacher_schedule_slots slot JOIN teachers t ON t.id=slot.teacher_id JOIN users u ON u.id=t.user_id
   JOIN classes c ON c.id=slot.class_id AND c.school_id=t.school_id JOIN schools k ON k.id=c.school_id JOIN academic_years y ON y.id=c.academic_year_id
   WHERE c.id=? AND c.school_id=? AND slot.day_of_week=? AND slot.effective_from<=? AND (slot.effective_to IS NULL OR slot.effective_to>=?) AND ? BETWEEN y.start_date AND y.end_date
   ORDER BY slot.start_time,slot.id
   """,(r,n)->new SelfServiceDto.ScheduleEntry(r.getLong("id"),r.getInt("day_of_week"),r.getObject("start_time",LocalTime.class),r.getObject("end_time",LocalTime.class),r.getLong("class_id"),r.getString("class_name"),r.getString("school_name"),r.getString("subjects"),r.getString("teacher_name")),child.classId(),child.schoolId(),today.getDayOfWeek().getValue(),today,today,today);
  var tasks=study.list(user,studentId).stream().filter(t->!t.done()).toList();
  return new Dashboard(profile(child),today,d.attendance(),d.weeks(),d.periods(),d.subjectPeriod(),d.subjects(),d.homework(),d.dueHomework(),d.evaluations(),d.announcements(),d.events(),d.sharedSupport(),courses,tasks.stream().limit(5).toList(),tasks.size(),tasks.stream().filter(t->t.dueOn()!=null&&t.dueOn().isBefore(today)).count());
 }
}
