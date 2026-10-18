package org.afritechinnovations.service.self;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.people.Student;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="parentDashboardTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/parent_dashboard_test")
class ParentDashboardPostgresTest {
 SingleConnectionDataSource source;JdbcTemplate jdbc;FamilySpaceService family;ParentDashboardService service;
 SelfServiceDto.StudentOverview child;SelfServiceDto.StudentGrades grades;
 @BeforeEach void setup(){
  source=new SingleConnectionDataSource(System.getProperty("parentDashboardTestUrl"),"parent_test","parent_test_only",true);jdbc=new JdbcTemplate(source);family=mock(FamilySpaceService.class);service=new ParentDashboardService(jdbc,family);
  when(family.requireGuardedChild(7L,11L)).thenReturn(Student.builder().id(11L).school(School.builder().id(1L).build()).build());
  child=mock(SelfServiceDto.StudentOverview.class);when(child.schoolId()).thenReturn(1L);when(child.classId()).thenReturn(101L);when(family.student(7L,11L)).thenReturn(child);
  grades=mock(SelfServiceDto.StudentGrades.class);when(grades.periods()).thenReturn(List.of());when(family.grades(7L,11L)).thenReturn(grades);
  jdbc.execute("CREATE TEMP TABLE academic_years(id BIGINT,start_date DATE,end_date DATE)");
  jdbc.execute("CREATE TEMP TABLE classes(id BIGINT,school_id BIGINT,academic_year_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE students(id BIGINT,school_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE attendances(id BIGINT,student_id BIGINT,class_id BIGINT,attendance_date DATE,status TEXT)");
  jdbc.execute("CREATE TEMP TABLE parent_portal_posts(id BIGINT,school_id BIGINT,class_id BIGINT,student_id BIGINT,author_id BIGINT,kind TEXT,title TEXT,due_date DATE,created_at TIMESTAMP)");
  jdbc.execute("CREATE TEMP TABLE homework_progress(post_id BIGINT,student_id BIGINT,school_id BIGINT,status TEXT)");
  jdbc.execute("CREATE TEMP TABLE evaluations(id BIGINT,class_subject_teacher_id BIGINT,title TEXT,eval_date DATE)");
  jdbc.execute("CREATE TEMP TABLE class_subject_teacher(id BIGINT,class_id BIGINT,subject_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE subjects(id BIGINT,name TEXT)");
  jdbc.execute("CREATE TEMP TABLE school_calendar_events(id BIGINT,school_id BIGINT,class_id BIGINT,title TEXT,starts_on DATE,ends_on DATE)");
  jdbc.execute("CREATE TEMP TABLE teacher_student_support(id BIGINT,user_id BIGINT,student_id BIGINT,class_id BIGINT,shared BOOLEAN,resolved BOOLEAN)");
  jdbc.update("INSERT INTO academic_years VALUES(1,'2026-09-01','2027-06-30'),(2,'2026-09-01','2027-06-30')");
  jdbc.update("INSERT INTO classes VALUES(101,1,1),(102,1,1),(201,2,2)");jdbc.update("INSERT INTO students VALUES(11,1),(12,1),(13,2)");
  jdbc.update("INSERT INTO attendances VALUES(1,11,101,'2026-10-12','ABSENT'),(2,11,101,'2026-10-12','LATE'),(3,11,101,'2026-10-13','PRESENT'),(4,11,101,'2026-10-14','EXCUSED'),(5,12,101,'2026-10-12','ABSENT'),(6,11,201,'2026-10-13','ABSENT'),(7,11,101,'2026-08-30','ABSENT'),(8,11,101,'2026-10-19','ABSENT')");
  jdbc.update("INSERT INTO parent_portal_posts VALUES(1,1,101,NULL,20,'HOMEWORK','À préparer','2026-10-17','2026-10-01'),(2,1,101,11,20,'HOMEWORK','Remis','2026-10-16','2026-10-01'),(3,1,101,11,20,'HOMEWORK','Corrigé','2026-10-15','2026-10-01'),(4,1,101,12,20,'HOMEWORK','Autre enfant','2026-10-15','2026-10-01'),(5,2,201,NULL,20,'HOMEWORK','Autre école','2026-10-15','2026-10-01'),(6,1,102,NULL,20,'HOMEWORK','Autre classe','2026-10-15','2026-10-01'),(7,1,101,11,20,'ANNOUNCEMENT','Annonce enfant',NULL,'2026-10-10'),(8,1,NULL,NULL,20,'ANNOUNCEMENT','Annonce école',NULL,'2026-10-11'),(9,1,101,12,20,'ANNOUNCEMENT','Privée autre enfant',NULL,'2026-10-12'),(10,2,NULL,NULL,20,'ANNOUNCEMENT','Autre école',NULL,'2026-10-12'),(11,1,NULL,NULL,20,'ANNOUNCEMENT','Ancienne année',NULL,'2026-06-12')");
  jdbc.update("INSERT INTO homework_progress VALUES(2,11,1,'SUBMITTED'),(3,11,1,'CORRECTED'),(1,12,1,'CORRECTED')");
  jdbc.update("INSERT INTO subjects VALUES(1,'Matière A')");jdbc.update("INSERT INTO class_subject_teacher VALUES(1,101,1),(2,201,1)");
  jdbc.update("INSERT INTO evaluations VALUES(1,1,'À venir','2026-10-20'),(2,1,'Trop loin','2026-11-10'),(3,1,'Passée','2026-10-10'),(4,2,'Autre école','2026-10-20')");
  jdbc.update("INSERT INTO school_calendar_events VALUES(1,1,NULL,'École','2026-10-20','2026-10-20'),(2,1,101,'Classe','2026-10-21','2026-10-21'),(3,1,102,'Autre classe','2026-10-20','2026-10-20'),(4,2,NULL,'Autre école','2026-10-20','2026-10-20')");
  jdbc.update("INSERT INTO teacher_student_support VALUES(1,20,11,101,true,false),(2,20,11,101,false,false),(3,20,12,101,true,false),(4,20,11,102,true,false),(5,20,11,101,true,true)");
 }
 @AfterEach void close(){source.destroy();}
 ParentDashboardService.Overview overview(){return service.overview(7L,11L,LocalDate.of(2026,10,18));}
 @Test void usesOnlyLatestActualDailyAttendanceWithinChildClassYearAndToday(){
  var data=overview();assertEquals(new ParentDashboardService.Attendance(1,0,1,1),data.attendance());assertEquals(7,data.weeks().size());assertEquals(0,data.weeks().getFirst().attendance().recorded());assertEquals(3,data.weeks().getLast().attendance().recorded());
 }
 @Test void homeworkTargetsAnnouncementsAndSharedSupportDoNotLeakOtherChildrenOrPrivatePlans(){
  var data=overview();assertEquals(new ParentDashboardService.Homework(1,1,1,1),data.homework());assertEquals(List.of(1L),data.dueHomework().stream().map(ParentDashboardService.DueHomework::id).toList());
  assertEquals(List.of(8L,7L),data.announcements().stream().map(ParentDashboardService.Notice::id).toList());assertEquals(1,data.sharedSupport());assertEquals(1,data.evaluations().size());assertEquals(2,data.events().size());
 }
 @Test void onlyPublishedOfficialResultsAreGraphedAndTrueZeroIsRetained(){
  var p1=mock(SelfServiceDto.PeriodGrades.class);when(p1.periodId()).thenReturn(1L);when(p1.periodName()).thenReturn("Période 1");when(p1.startDate()).thenReturn(LocalDate.of(2026,9,1));when(p1.published()).thenReturn(true);
  var bulletin=mock(OwnerGradeDto.Bulletin.class);when(bulletin.average()).thenReturn(BigDecimal.ZERO);when(bulletin.lines()).thenReturn(List.of(new OwnerGradeDto.BulletinLine("Matière A",null,BigDecimal.ONE,BigDecimal.ZERO,null,null,null,null,null),new OwnerGradeDto.BulletinLine("Sans note",null,BigDecimal.ONE,null,null,null,null,null,null)));when(p1.bulletin()).thenReturn(bulletin);
  var pending=mock(SelfServiceDto.PeriodGrades.class);when(pending.periodId()).thenReturn(2L);when(pending.periodName()).thenReturn("Période 2");when(pending.startDate()).thenReturn(LocalDate.of(2026,10,1));when(pending.published()).thenReturn(false);
  when(grades.periods()).thenReturn(List.of(p1,pending));var result=overview();assertEquals(BigDecimal.ZERO,result.periods().getFirst().average());assertNull(result.periods().getLast().average());assertEquals("Période 1",result.subjectPeriod());assertEquals(BigDecimal.ZERO,result.subjects().getFirst().average());assertNull(result.subjects().getLast().average());
 }
 @Test void futureYearAndChildWithoutClassDoNotInventPresenceOrGrades(){
  jdbc.update("UPDATE academic_years SET start_date='2027-01-01' WHERE id=1");assertTrue(overview().weeks().isEmpty());assertEquals(0,overview().attendance().recorded());
  when(child.classId()).thenReturn(null);var result=overview();assertTrue(result.weeks().isEmpty());assertTrue(result.subjects().isEmpty());assertEquals(0,result.homework().todo());
 }
}
