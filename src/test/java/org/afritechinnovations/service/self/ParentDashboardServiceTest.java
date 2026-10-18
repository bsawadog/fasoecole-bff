package org.afritechinnovations.service.self;
import org.junit.jupiter.api.*;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.common.School;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ParentDashboardServiceTest {
 JdbcTemplate jdbc=mock(JdbcTemplate.class);FamilySpaceService family=mock(FamilySpaceService.class);
 ParentDashboardService service=new ParentDashboardService(jdbc,family);
 @AfterEach void reset(){RequestContextHolder.resetRequestAttributes();}
 @Test void rejectsUnrelatedStudentsAndRevokedParentLinksBeforeReadingAnything(){
  when(family.requireGuardedChild(7L,11L)).thenThrow(new AccessDeniedException("Forbidden"));
  assertThrows(AccessDeniedException.class,()->service.overview(7L,11L));verifyNoInteractions(jdbc);verify(family,never()).grades(anyLong(),anyLong());
 }
 @Test void rejectsAnotherSelectedSchoolBeforeComputingGrades(){
  when(family.requireGuardedChild(7L,11L)).thenReturn(Student.builder().id(11L).school(School.builder().id(1L).build()).build());
  var request=new MockHttpServletRequest();request.addHeader("X-Academic-School","2");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  assertThrows(AccessDeniedException.class,()->service.overview(7L,11L));verifyNoInteractions(jdbc);verify(family,never()).student(anyLong(),anyLong());
 }
}
