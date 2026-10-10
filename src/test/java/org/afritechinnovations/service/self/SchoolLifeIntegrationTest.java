package org.afritechinnovations.service.self;

import org.afritechinnovations.dto.self.SchoolLifeDto.*;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.model.academic.*;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.security.AccessGuard;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in integration checks against a disposable local PostgreSQL database. */
@EnabledIfSystemProperty(named="schoolLifeTestUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/school_life_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchoolLifeIntegrationTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    SchoolLifeService service;
    AccessGuard guard;
    FamilySpaceService family;
    TeacherSpaceService teacher;
    School school=School.builder().id(1001L).build();
    AcademicYear year=AcademicYear.builder().id(1002L).isCurrent(true).build();
    SchoolClass cls=SchoolClass.builder().id(1003L).school(school).academicYear(year).build();

    @BeforeAll void migrate() {
        var source=new DriverManagerDataSource(System.getProperty("schoolLifeTestUrl"),"postgres","disposable-test-only");
        jdbc=new JdbcTemplate(source);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc.update("INSERT INTO users(id,first_name,last_name,password_hash) VALUES(1101,'Owner','A','test'),(1102,'Teacher','A','test'),(1103,'Parent','A','test'),(1104,'Child','A','test'),(1105,'Child','B','test'),(1106,'Child','Other','test') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(1001,'School A','PRIMAIRE',1101),(1011,'School B','PRIMAIRE',1101) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO academic_years(id,school_id,label,start_date,end_date,is_current) VALUES(1002,1001,'2026-2027','2026-09-01','2027-07-01',true),(1012,1011,'2026-2027','2026-09-01','2027-07-01',true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO levels(id,school_id,name,cycle) VALUES(1004,1001,'CM1','PRIMAIRE'),(1014,1011,'CM1','PRIMAIRE') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO classes(id,school_id,academic_year_id,level_id,name) VALUES(1003,1001,1002,1004,'CM1'),(1013,1011,1012,1014,'CM1') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO students(id,user_id,school_id,registration_number) VALUES(1005,1104,1001,'A'),(1006,1105,1001,'B'),(1015,1106,1011,'C') ON CONFLICT DO NOTHING");
        jdbc.update("DELETE FROM student_enrollments WHERE student_id IN (1005,1006,1015)");
        jdbc.update("INSERT INTO student_enrollments(student_id,class_id,academic_year_id,status) VALUES(1005,1003,1002,'ACTIVE'),(1006,1003,1002,'ACTIVE'),(1015,1013,1012,'ACTIVE')");
        jdbc.update("INSERT INTO parent_portal_posts(id,school_id,class_id,author_id,kind,title,content,due_date) VALUES(1020,1001,1003,1102,'HOMEWORK','Homework','Exercises',CURRENT_DATE+7),(1021,1001,1003,1101,'HOMEWORK','Other teacher','Exercises',CURRENT_DATE+7) ON CONFLICT DO NOTHING");
    }
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE school_calendar_events,student_observations,school_document_requests,library_loans,library_books,homework_progress RESTART IDENTITY CASCADE");
        guard=mock(AccessGuard.class); family=mock(FamilySpaceService.class); teacher=mock(TeacherSpaceService.class);
        when(guard.currentUserId()).thenReturn(1102L);
        when(teacher.requireTaughtClass(1102L,1003L)).thenReturn(cls);
        var child=Student.builder().id(1005L).school(school).build();
        when(family.requireGuardedChild(1102L,1005L)).thenReturn(child);
        var detail=mock(SelfServiceDto.StudentOverview.class);
        when(detail.classId()).thenReturn(1003L);
        when(family.student(1102L,1005L)).thenReturn(detail);
        service=new SchoolLifeService(jdbc,guard,family,teacher);
    }
    @Test void migrationAndOverviewQueriesWorkAndIsolateSchoolsAndChildren() {
        var data=service.ownerOverview(1001L);
        assertEquals(2,data.students().size());
        assertEquals(4,data.homeworks().size());
        var child=service.studentOverview(1005L);
        assertEquals(1,child.students().size());
        assertTrue(child.homeworks().stream().allMatch(h->h.studentId()==1005L && !h.editable()));
        assertEquals(1,service.ownerOverview(1011L).students().size());
    }
    @Test void calendarScopesAndDatesAreEnforced() {
        service.createEvent(1001L,new EventRequest(null,"Holiday","",EventKind.HOLIDAY,LocalDate.now(),LocalDate.now().plusDays(1)));
        assertEquals(1,service.teacherOverview(1003L).events().size());
        assertEquals(1,service.studentOverview(1005L).events().size());
        assertEquals(0,service.ownerOverview(1011L).events().size());
        assertThrows(IllegalArgumentException.class,()->service.createEvent(1001L,new EventRequest(null,"Bad","",EventKind.EVENT,LocalDate.now(),LocalDate.now().minusDays(1))));
    }
    @Test void disciplineIsInternalUntilSharedAndRejectsStudentFromAnotherSchool() {
        service.teacherObservation(1003L,new ObservationRequest(1003L,1005L,ObservationKind.INCIDENT,LocalDate.now(),"Observation","",false));
        assertEquals(1,service.teacherOverview(1003L).observations().size());
        assertTrue(service.studentOverview(1005L).observations().isEmpty());
        service.resolveObservation(1001L,1L,new ObservationDecision("Discussed",true,true));
        assertEquals(1,service.studentOverview(1005L).observations().size());
        assertThrows(AccessDeniedException.class,()->service.teacherObservation(1003L,new ObservationRequest(1003L,1015L,ObservationKind.INCIDENT,LocalDate.now(),"Bad","",true)));
    }
    @Test void requestsFollowWorkflowAndAreScopedToTheChild() {
        service.requestDocument(1005L,new DocumentRequest(DocumentKind.SCHOOL_CERTIFICATE,"Request"));
        assertEquals("PENDING",service.studentOverview(1005L).requests().getFirst().status());
        assertTrue(service.teacherOverview(1003L).requests().isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.decideDocument(1011L,1L,new DocumentDecision(RequestStatus.READY,"Office")));
        service.decideDocument(1001L,1L,new DocumentDecision(RequestStatus.IN_PROGRESS,"Processing"));
        service.decideDocument(1001L,1L,new DocumentDecision(RequestStatus.READY,"Collect at office"));
        assertEquals("READY",service.studentOverview(1005L).requests().getFirst().status());
        assertThrows(IllegalArgumentException.class,()->service.cancelDocument(1005L,1L));
    }
    @Test void libraryStockAndReturnWorkflowAreEnforced() {
        when(guard.schoolOfStudent(1005L)).thenReturn(1001L);
        service.createBook(1001L,new BookRequest("Book","Author","REF",1));
        tx.executeWithoutResult(t->service.lend(1001L,new LoanRequest(1L,1005L,LocalDate.now().plusDays(7))));
        assertEquals(0,service.ownerOverview(1001L).books().getFirst().available());
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(t->service.lend(1001L,new LoanRequest(1L,1005L,LocalDate.now().plusDays(7)))));
        service.returnBook(1001L,1L);
        assertEquals(1,service.ownerOverview(1001L).books().getFirst().available());
        assertThrows(IllegalArgumentException.class,()->service.returnBook(1001L,1L));
    }
    @Test void homeworkUpdatesArePerStudentAndRequireThePostAuthor() {
        service.teacherHomework(1003L,1020L,1005L,new HomeworkDecision(HomeworkStatus.CORRECTED,"Well done"));
        var child=service.studentOverview(1005L).homeworks().stream().filter(h->h.postId()==1020L).findFirst().orElseThrow();
        assertEquals("CORRECTED",child.status()); assertEquals("Well done",child.feedback());
        assertThrows(AccessDeniedException.class,()->service.teacherHomework(1003L,1021L,1005L,new HomeworkDecision(HomeworkStatus.SUBMITTED,"")));
        assertThrows(AccessDeniedException.class,()->service.teacherHomework(1003L,1020L,1015L,new HomeworkDecision(HomeworkStatus.SUBMITTED,"")));
        assertEquals("TO_DO",service.ownerOverview(1001L).homeworks().stream().filter(h->h.postId()==1020L && h.studentId()==1006L).findFirst().orElseThrow().status());
    }

    @Test void simultaneousLoansCannotExceedTheNumberOfCopies() throws Exception {
        when(guard.schoolOfStudent(1005L)).thenReturn(1001L);
        service.createBook(1001L,new BookRequest("One copy","Author","SINGLE",1));
        var ready=new java.util.concurrent.CountDownLatch(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> attempt=()->{
                ready.countDown(); start.await();
                try { tx.executeWithoutResult(t->service.lend(1001L,new LoanRequest(1L,1005L,LocalDate.now().plusDays(7)))); return true; }
                catch (IllegalArgumentException exhausted) { return false; }
            };
            var first=executor.submit(attempt); var second=executor.submit(attempt);
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM library_loans WHERE returned_on IS NULL",Integer.class));
    }

    @Test void moduleFilterReturnsOnlyTheRequestedModule() {
        service.createBook(1001L,new BookRequest("Book","Author","REF",1));
        service.createEvent(1001L,new EventRequest(null,"Meeting","",EventKind.MEETING,LocalDate.now(),LocalDate.now()));
        var data=service.ownerOverview(1001L,LifeModule.CALENDAR);
        assertEquals(1,data.events().size()); assertTrue(data.books().isEmpty()); assertTrue(data.homeworks().isEmpty());
    }

    @Test void schoolExportIncludesTheNewModules() throws Exception {
        var schools=mock(org.afritechinnovations.repository.common.SchoolRepository.class);
        var exportSchool=School.builder().id(1001L).name("School A")
                .owner(org.afritechinnovations.model.common.User.builder().id(1102L).build()).build();
        when(schools.findById(1001L)).thenReturn(java.util.Optional.of(exportSchool));
        var exporter=new org.afritechinnovations.service.export.SchoolExportService(schools,jdbc,guard);
        var file=exporter.create(1001L,false);
        try (var zip=new java.util.zip.ZipFile(file.path().toFile())) {
            var workbook=new String(zip.getInputStream(zip.getEntry("xl/workbook.xml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(workbook.contains("Calendrier scolaire")); assertTrue(workbook.contains("Discipline"));
        } finally { java.nio.file.Files.deleteIfExists(file.path()); }
    }
}
