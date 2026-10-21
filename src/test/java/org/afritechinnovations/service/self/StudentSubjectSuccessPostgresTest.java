package org.afritechinnovations.service.self;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="studentSubjectTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/student_subject_test")
class StudentSubjectSuccessPostgresTest {
 private SingleConnectionDataSource source;
 private StudentDashboardService service;
 @BeforeEach void setup(){
  source=new SingleConnectionDataSource(System.getProperty("studentSubjectTestUrl"),"student_test","student_test_only",true);
  var jdbc=new JdbcTemplate(source);
  service=new StudentDashboardService(null,null,null,jdbc);
  jdbc.execute("CREATE TEMP TABLE academic_years(id BIGINT,start_date DATE,end_date DATE)");
  jdbc.execute("CREATE TEMP TABLE classes(id BIGINT,school_id BIGINT,academic_year_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE subjects(id BIGINT,name TEXT)");
  jdbc.execute("CREATE TEMP TABLE class_subject_teacher(id BIGINT,class_id BIGINT,subject_id BIGINT,active BOOLEAN)");
  jdbc.execute("CREATE TEMP TABLE grades(id BIGINT,class_subject_teacher_id BIGINT,student_id BIGINT,value NUMERIC,max_value NUMERIC,grade_date DATE)");
  jdbc.update("INSERT INTO academic_years VALUES(1,'2026-09-01','2027-06-30'),(2,'2025-09-01','2026-06-30')");
  jdbc.update("INSERT INTO classes VALUES(101,1,1),(102,1,1),(201,2,1),(301,1,2)");
  jdbc.update("INSERT INTO subjects VALUES(1,'Maths'),(2,'English'),(3,'Science'),(4,'History'),(5,'Inactive')");
  jdbc.update("INSERT INTO class_subject_teacher VALUES(1,101,1,true),(2,101,1,true),(3,101,2,true),(4,101,3,true),(5,101,4,false),(6,101,5,false),(7,102,1,true),(8,201,1,true),(9,301,1,true)");
  jdbc.update("INSERT INTO grades VALUES(1,1,11,12,20,'2026-10-01'),(2,2,11,8,10,'2026-11-01'),(3,1,12,20,20,'2026-10-01'),(4,1,11,20,20,'2025-10-01'),(5,4,11,0,20,'2026-10-01'),(6,5,11,9,20,'2026-10-01'),(7,7,11,20,20,'2026-10-01'),(8,8,11,20,20,'2026-10-01'),(9,9,11,20,20,'2025-10-01')");
 }
 @AfterEach void close(){source.destroy();}
 @Test void averagesNormalizedGradesAcrossPeriodsAndTeachersWithoutLeakingOtherStudentsClassesOrYears(){
  var result=service.subjectSuccess(11L,101L,1L);
  assertEquals(List.of("English","History","Maths","Science"),result.stream().map(StudentDashboardService.SubjectSuccess::name).toList());
  var maths=result.stream().filter(s->s.subjectId()==1L).findFirst().orElseThrow();
  assertEquals(0,new BigDecimal("70").compareTo(maths.percentage()));
  assertEquals(2,maths.gradeCount());
  var history=result.stream().filter(s->s.subjectId()==4L).findFirst().orElseThrow();
  assertEquals(0,new BigDecimal("45").compareTo(history.percentage()));
 }
 @Test void includesUnmarkedSubjectsAtZeroAndDistinguishesARealZeroGrade(){
  var result=service.subjectSuccess(11L,101L,1L);
  var english=result.stream().filter(s->s.subjectId()==2L).findFirst().orElseThrow();
  assertEquals(0,english.percentage().signum());assertEquals(0,english.gradeCount());
  var science=result.stream().filter(s->s.subjectId()==3L).findFirst().orElseThrow();
  assertEquals(0,science.percentage().signum());assertEquals(1,science.gradeCount());
 }
 @Test void emptyWithoutAClassOrWithTheWrongSchool(){
  assertTrue(service.subjectSuccess(11L,null,1L).isEmpty());
  assertTrue(service.subjectSuccess(11L,101L,2L).isEmpty());
 }
}
