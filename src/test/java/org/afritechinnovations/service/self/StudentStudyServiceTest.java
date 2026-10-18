package org.afritechinnovations.service.self;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.people.Student;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StudentStudyServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final FamilySpaceService family=mock(FamilySpaceService.class);
 final StudentStudyService service=new StudentStudyService(jdbc,family);
 @Test void rejectedIdentityNeverReadsOrWritesTasks(){
  when(family.requireOwnStudent(7L,11L)).thenThrow(new AccessDeniedException("denied"));
  assertThrows(AccessDeniedException.class,()->service.list(7L,11L));
  assertThrows(AccessDeniedException.class,()->service.save(7L,11L,null,new StudentStudyService.Request("Study","",null,false)));
  assertThrows(AccessDeniedException.class,()->service.remove(7L,11L,1L));
  verifyNoInteractions(jdbc);
 }
 @Test void modifyingOrDeletingAnotherTaskFailsWithOwnerScopedSql(){
  when(family.requireOwnStudent(7L,11L)).thenReturn(Student.builder().id(11L).school(School.builder().id(1L).build()).build());
  assertThrows(AccessDeniedException.class,()->service.save(7L,11L,99L,new StudentStudyService.Request("Study","",null,true)));
  verify(jdbc).update("UPDATE student_study_tasks SET title=?,details=?,due_on=?,done=? WHERE id=? AND student_id=? AND user_id=?","Study","",null,true,99L,11L,7L);
  assertThrows(AccessDeniedException.class,()->service.remove(7L,11L,99L));
  verify(jdbc).update("DELETE FROM student_study_tasks WHERE id=? AND student_id=? AND user_id=?",99L,11L,7L);
 }
}
