package org.afritechinnovations.service.academic;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="dashboardChartTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/dashboard_chart_test")
class ClassTimetablePostgresTest {
 SingleConnectionDataSource source;JdbcTemplate jdbc;ClassTimetableService service;
 @BeforeEach void setup(){
  source=new SingleConnectionDataSource(System.getProperty("dashboardChartTestUrl"),"chart_test","chart_test_only",true);jdbc=new JdbcTemplate(source);service=new ClassTimetableService(jdbc);
  jdbc.execute("CREATE TEMP TABLE schools(id BIGINT,name TEXT)");
  jdbc.execute("CREATE TEMP TABLE academic_years(id BIGINT,school_id BIGINT,label TEXT,start_date DATE,end_date DATE,is_current BOOLEAN)");
  jdbc.execute("CREATE TEMP TABLE classes(id BIGINT,school_id BIGINT,academic_year_id BIGINT,name TEXT)");
  jdbc.execute("CREATE TEMP TABLE teachers(id BIGINT,school_id BIGINT,user_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE users(id BIGINT,first_name TEXT,last_name TEXT)");
  jdbc.execute("CREATE TEMP TABLE subjects(id BIGINT,name TEXT)");
  jdbc.execute("CREATE TEMP TABLE class_subject_teacher(class_id BIGINT,teacher_id BIGINT,subject_id BIGINT,active BOOLEAN)");
  jdbc.execute("CREATE TEMP TABLE teacher_schedule_slots(id BIGINT,teacher_id BIGINT,class_id BIGINT,day_of_week INTEGER,start_time TIME,end_time TIME,effective_from DATE,effective_to DATE)");
  jdbc.update("INSERT INTO schools VALUES(1,'Ecole'),(2,'Autre')");
  jdbc.update("INSERT INTO academic_years VALUES(1,1,'2026','2026-09-01','2027-06-30',true),(2,2,'Autre','2026-09-01','2027-06-30',true),(3,1,'Archive','2025-09-01','2026-06-30',false)");
  jdbc.update("INSERT INTO classes VALUES(1,1,1,'CP1'),(2,2,2,'Autre'),(3,1,3,'Archive')");
  jdbc.update("INSERT INTO users VALUES(1,'Awa','Traore'),(2,'Issa','Diallo')");
  jdbc.update("INSERT INTO teachers VALUES(1,1,1),(2,2,2)");
  jdbc.update("INSERT INTO subjects VALUES(1,'Maths'),(2,'Francais'),(3,'Ancienne')");
  jdbc.update("INSERT INTO class_subject_teacher VALUES(1,1,1,true),(1,1,2,true),(1,1,3,false)");
  jdbc.update("INSERT INTO teacher_schedule_slots VALUES(1,1,1,1,'08:00','09:00','2026-09-01','2026-10-12'),(2,1,1,1,'09:00','10:00','2026-10-13',null),(3,1,1,3,'08:00','09:00','2026-10-14',null),(4,1,1,3,'10:00','11:00','2026-10-15',null),(5,2,2,1,'08:00','09:00','2026-09-01',null)");
 }
 @AfterEach void close(){RequestContextHolder.resetRequestAttributes();source.destroy();}
 @Test void usesTheActualDayOfEachSlotAndInclusiveEffectiveDates(){
  var result=service.overview(1L,1L,LocalDate.of(2026,10,14));
  assertEquals(LocalDate.of(2026,10,12),result.weekStart());
  assertEquals(List.of(1L,3L),result.lessons().stream().map(ClassTimetableService.Lesson::id).toList());
  assertEquals(LocalDate.of(2026,10,14),result.lessons().getLast().date());
  assertEquals("Francais, Maths",result.lessons().getFirst().subjects());
  assertEquals("Awa Traore",result.lessons().getFirst().teacherName());
  assertEquals(List.of(2L,3L,4L),service.overview(1L,1L,LocalDate.of(2026,10,21)).lessons().stream().map(ClassTimetableService.Lesson::id).toList());
 }
 @Test void excludesForeignClassesYearsAndOutOfYearOccurrences(){
  assertThrows(IllegalArgumentException.class,()->service.overview(1L,2L,LocalDate.of(2026,10,14)));
  assertThrows(IllegalArgumentException.class,()->service.overview(1L,3L,LocalDate.of(2026,10,14)));
  assertThrows(IllegalArgumentException.class,()->service.overview(1L,1L,LocalDate.of(2027,7,1)));
  assertTrue(service.overview(1L,1L,LocalDate.of(2026,9,1)).lessons().isEmpty());
 }
 @Test void supportsSelectedArchiveAndEmptyClassRoster(){
  var request=new MockHttpServletRequest();request.addHeader("X-Academic-School","1");request.addHeader("X-Academic-Year","3");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  var result=service.overview(1L,null,LocalDate.of(2025,10,1));assertEquals(3L,result.classId());assertTrue(result.lessons().isEmpty());
  request=new MockHttpServletRequest();request.addHeader("X-Academic-School","1");request.addHeader("X-Academic-Year","2");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  assertThrows(IllegalArgumentException.class,()->service.overview(1L,null,null));
 }
}
