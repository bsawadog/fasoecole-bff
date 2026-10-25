package org.afritechinnovations.service.self;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.service.academic.SelectedAcademicYear;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class StudentStudyService {
 private final JdbcTemplate jdbc;private final FamilySpaceService family;
 public record Task(Long id,String title,String details,LocalDate dueOn,boolean done) {}
 public record Request(@NotBlank @Size(max=160) String title,@NotNull @Size(max=2000) String details,LocalDate dueOn,boolean done) {}
 private void own(Long user,Long studentId){var student=family.requireOwnStudent(user,studentId);if(!SelectedAcademicYear.schoolMatches(student.getSchool().getId()))throw new AccessDeniedException("Choisissez votre établissement.");}
 public List<Task> list(Long user,Long studentId){own(user,studentId);return jdbc.query("SELECT * FROM student_study_tasks WHERE student_id=? AND user_id=? ORDER BY done,due_on NULLS LAST,id DESC",(r,n)->new Task(r.getLong("id"),r.getString("title"),r.getString("details"),r.getObject("due_on",LocalDate.class),r.getBoolean("done")),studentId,user);}
 @Transactional public void save(Long user,Long studentId,Long id,Request r){
  own(user,studentId);
  if(id==null)jdbc.update("INSERT INTO student_study_tasks(student_id,user_id,title,details,due_on,done) VALUES(?,?,?,?,?,?)",studentId,user,r.title().trim(),r.details().trim(),r.dueOn(),r.done());
  else changed(jdbc.update("UPDATE student_study_tasks SET title=?,details=?,due_on=?,done=? WHERE id=? AND student_id=? AND user_id=?",r.title().trim(),r.details().trim(),r.dueOn(),r.done(),id,studentId,user));
 }
 @Transactional public void remove(Long user,Long studentId,Long id){own(user,studentId);changed(jdbc.update("DELETE FROM student_study_tasks WHERE id=? AND student_id=? AND user_id=?",id,studentId,user));}
 private void changed(int count){if(count==0)throw new AccessDeniedException("Rappel introuvable ou non autorisé.");}
}
