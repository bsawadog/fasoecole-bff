package org.afritechinnovations.service.self;
import org.afritechinnovations.dto.self.TeacherAttendanceDto;
import org.afritechinnovations.model.academic.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.academic.*;
import org.afritechinnovations.repository.people.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class TeacherAttendanceBatchTest {
 @Mock TeacherRepository teachers;@Mock SchoolClassRepository classes;@Mock ClassSubjectTeacherRepository assignments;
 @Mock StudentEnrollmentRepository enrollments;@Mock AttendanceRepository attendanceRepository;
 @InjectMocks TeacherSpaceService service;
 SchoolClass cls;List<StudentEnrollment> roster;
 @BeforeEach void setup(){
  var school=School.builder().id(1L).build();
  var year=AcademicYear.builder().id(1L).startDate(LocalDate.now().minusYears(1)).endDate(LocalDate.now().plusYears(1)).build();
  cls=SchoolClass.builder().id(11L).school(school).academicYear(year).build();
  when(teachers.findByUserId(5L)).thenReturn(List.of(Teacher.builder().id(50L).build()));
  when(assignments.existsBySchoolClassIdAndTeacherIdAndActiveTrue(anyLong(),eq(50L))).thenAnswer(call->Long.valueOf(11L).equals(call.getArgument(0)));
  lenient().when(classes.findById(11L)).thenReturn(Optional.of(cls));
  roster=java.util.stream.LongStream.rangeClosed(1,3).mapToObj(id->StudentEnrollment.builder().id(id).schoolClass(cls).academicYear(year).student(Student.builder().id(id).user(User.builder().id(100+id).firstName("Eleve").lastName("Nom"+id).build()).build()).build()).toList();
  lenient().when(enrollments.findStudentsWithUserByClassIdAndStatusIn(anyLong(),any())).thenReturn(roster);
 }
 TeacherAttendanceDto.Entry entry(long id,AttendanceStatus status,AttendanceStatus previous){return new TeacherAttendanceDto.Entry(id,status,null,previous);}
 TeacherAttendanceDto.Batch batch(TeacherAttendanceDto.Entry... entries){return new TeacherAttendanceDto.Batch(LocalDate.now(),List.of(entries));}
 @Test void savesDefaultPresentsAndEditsWithoutChangingOtherRecordedStatuses(){
  var late=Attendance.builder().student(roster.get(1).getStudent()).status(AttendanceStatus.LATE).build();
  var excused=Attendance.builder().student(roster.get(2).getStudent()).status(AttendanceStatus.EXCUSED).build();
  when(attendanceRepository.findByClassBetween(anyLong(),any(),any())).thenReturn(List.of(late,excused));
  service.saveAttendanceBatch(5L,11L,batch(entry(1,AttendanceStatus.PRESENT,null),entry(2,AttendanceStatus.ABSENT,AttendanceStatus.LATE)));
  var saved=ArgumentCaptor.forClass(Attendance.class);verify(attendanceRepository,times(2)).save(saved.capture());
  assertEquals(List.of(1L,2L),saved.getAllValues().stream().map(a->a.getStudent().getId()).toList());
  assertEquals(AttendanceStatus.PRESENT,saved.getAllValues().getFirst().getStatus());assertEquals(AttendanceStatus.ABSENT,late.getStatus());assertEquals(AttendanceStatus.EXCUSED,excused.getStatus());
 }
 @Test void validatesAllStudentsBeforeWritingAnyAttendance(){
  assertThrows(AccessDeniedException.class,()->service.saveAttendanceBatch(5L,11L,batch(entry(1,AttendanceStatus.PRESENT,null),entry(999,AttendanceStatus.PRESENT,null))));
  verify(attendanceRepository,never()).save(any());
 }
 @Test void refusesToReplaceAnAbsenceRecordedSinceTheRosterWasLoaded(){
  when(attendanceRepository.findByClassBetween(anyLong(),any(),any())).thenReturn(List.of(Attendance.builder().student(roster.get(1).getStudent()).status(AttendanceStatus.ABSENT).build()));
  assertThrows(IllegalArgumentException.class,()->service.saveAttendanceBatch(5L,11L,batch(entry(1,AttendanceStatus.PRESENT,null),entry(2,AttendanceStatus.PRESENT,null))));
  verify(attendanceRepository,never()).save(any());
 }
 @Test void refusesFutureDates(){
  assertThrows(IllegalArgumentException.class,()->service.saveAttendanceBatch(5L,11L,new TeacherAttendanceDto.Batch(LocalDate.now().plusDays(1),List.of(entry(1,AttendanceStatus.PRESENT,null)))));
  verify(attendanceRepository,never()).save(any());
 }
 @Test void refusesClassesWithoutAnActiveTeacherAssignment(){
  assertThrows(AccessDeniedException.class,()->service.saveAttendanceBatch(5L,11L+1,batch(entry(1,AttendanceStatus.PRESENT,null))));
  verifyNoInteractions(attendanceRepository);
 }
}
