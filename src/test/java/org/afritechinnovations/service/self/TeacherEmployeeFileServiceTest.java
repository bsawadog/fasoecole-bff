package org.afritechinnovations.service.self;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class TeacherEmployeeFileServiceTest {
 @Mock TeacherRepository teachers;@Mock TeacherWorkService work;@Mock ConversationMessagingService messages;
 @InjectMocks TeacherEmployeeFileService service;
 Teacher teacher;School school;
 @BeforeEach void setup(){school=School.builder().id(1L).name("Ecole A").status(SchoolStatus.ACTIVE).owner(User.builder().id(99L).firstName("Direction").lastName("Ecole").active(true).approved(true).build()).build();teacher=Teacher.builder().id(11L).school(school).user(User.builder().id(5L).build()).employeeNumber("EMP-11").build();}
 @Test void listsOnlyEmploymentRecordsForTheConnectedUserWithoutRequiringClassAssignments(){when(teachers.findByUserId(5L)).thenReturn(List.of(teacher));assertEquals(List.of(11L),service.list(5L).stream().map(TeacherEmployeeFileService.Choice::teacherId).toList());verify(teachers).findByUserId(5L);verifyNoInteractions(work,messages);}
 @Test void readsOwnFileThroughTheSelfReadMethodWithoutAdministrativePrivileges(){when(teachers.findById(11L)).thenReturn(Optional.of(teacher));var f=service.read(5L,11L,"2026-09");assertEquals("Ecole A",f.schoolName());assertTrue(f.canRequest());verify(work).getOwnTeacherDetail(11L,"2026-09",5L);verify(work,never()).getTeacherDetail(anyLong(),anyString(),anyLong(),anyBoolean());verify(teachers,never()).save(any());verifyNoInteractions(messages);}
 @Test void rejectsReadingAndContactRequestsAgainstAnotherEmployeesFile(){when(teachers.findById(11L)).thenReturn(Optional.of(teacher));assertThrows(AccessDeniedException.class,()->service.read(6L,11L,"2026-09"));assertThrows(AccessDeniedException.class,()->service.request(6L,11L,new TeacherEmployeeFileService.ActionRequest("Correction","Mon matricule")));verifyNoInteractions(work,messages);}
 @Test void fixesTheRecipientToTheSchoolOwnerAndSendsOnlyAMessage(){when(teachers.findById(11L)).thenReturn(Optional.of(teacher));var summary=mock(FamilyContactDto.ConversationSummary.class);when(messages.sendTeacherMessage(eq(5L),any(),anyList())).thenReturn(List.of(summary));assertSame(summary,service.request(5L,11L,new TeacherEmployeeFileService.ActionRequest("Correction du matricule","Le numéro doit être EMP-12.")));var request=ArgumentCaptor.forClass(FamilyContactDto.TeacherMessageRequest.class);verify(messages).sendTeacherMessage(eq(5L),request.capture(),eq(List.of()));assertEquals(1L,request.getValue().schoolId());assertNull(request.getValue().classId());assertEquals(List.of(99L),request.getValue().recipientUserIds());assertTrue(request.getValue().subject().startsWith("[Fiche employé]"));assertTrue(request.getValue().content().contains("EMP-11"));assertTrue(request.getValue().content().contains("\nÉtablissement :"));verifyNoInteractions(work);verify(teachers,never()).save(any());}
 @Test void disablesRequestsWhenTheOwnerIsTheTeacherOrTheSchoolIsInactive(){when(teachers.findById(11L)).thenReturn(Optional.of(teacher));school.getOwner().setId(5L);assertFalse(service.read(5L,11L,"2026-09").canRequest());assertThrows(IllegalArgumentException.class,()->service.request(5L,11L,new TeacherEmployeeFileService.ActionRequest("Objet","Message")));school.getOwner().setId(99L);school.setStatus(SchoolStatus.SUSPENDED);assertThrows(IllegalArgumentException.class,()->service.request(5L,11L,new TeacherEmployeeFileService.ActionRequest("Objet","Message")));verifyNoInteractions(messages);}
}
