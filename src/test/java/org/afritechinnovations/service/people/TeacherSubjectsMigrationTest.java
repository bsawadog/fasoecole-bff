package org.afritechinnovations.service.people;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "teacherSubjectsTestUrl", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/teacher_subjects_test")
class TeacherSubjectsMigrationTest {
    @Test void preservesDistinctExistingTeachingSubjectsAndCascadesOnlyTheLinks() {
        var source = new DriverManagerDataSource(System.getProperty("teacherSubjectsTestUrl"), "postgres", "disposable-test-only");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("46").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO users(id,first_name,last_name,password_hash) VALUES(99001,'Owner','Test','hash')");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(99001,'Test','PRIMAIRE',99001)");
        jdbc.update("INSERT INTO academic_years(id,school_id,label,start_date,end_date,is_current) VALUES(99001,99001,'2026-2027','2026-10-01','2027-09-30',true)");
        jdbc.update("INSERT INTO levels(id,school_id,name,cycle) VALUES(99001,99001,'CP1','PRIMAIRE')");
        jdbc.update("INSERT INTO classes(id,school_id,academic_year_id,level_id,name) VALUES(99001,99001,99001,99001,'A'),(99002,99001,99001,99001,'B')");
        jdbc.update("INSERT INTO subjects(id,school_id,name) VALUES(99001,99001,'Maths'),(99002,99001,'French')");
        jdbc.update("INSERT INTO teachers(id,user_id,school_id,employee_number) VALUES(99001,99001,99001,'EMP-TEST')");
        jdbc.update("INSERT INTO class_subject_teacher(class_id,teacher_id,subject_id) VALUES(99001,99001,99001),(99002,99001,99001),(99001,99001,99002)");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        assertEquals(List.of(99001L, 99002L), jdbc.queryForList("SELECT subject_id FROM teacher_subjects WHERE teacher_id=99001 ORDER BY subject_id", Long.class));
        jdbc.update("DELETE FROM teachers WHERE id=99001");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM teacher_subjects", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM subjects", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE id=99001", Integer.class));
    }
}
