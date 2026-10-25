package org.afritechinnovations.service.common;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Only runs against the disposable database created for this migration test. */
@EnabledIfSystemProperty(named = "staffModuleTestUrl", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/staff_modules_test")
class StaffModuleMigrationTest {
    @Test
    void splittingPermissionsPreservesExistingAccessAndSupportsIndependentSelection() {
        var source = new DriverManagerDataSource(System.getProperty("staffModuleTestUrl"), "postgres", "disposable-test-only");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("45").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO users(id,first_name,last_name,password_hash) VALUES(99001,'Owner','Test','hash'),(99002,'Staff','Test','hash')");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(99001,'Test','PRIMAIRE',99001)");
        jdbc.update("INSERT INTO school_staff(id,school_id,user_id,job_title) VALUES(99001,99001,99001,'Students'),(99002,99001,99002,'Expenses')");
        jdbc.update("INSERT INTO school_staff_modules(staff_id,module) VALUES(99001,'STUDENTS'),(99002,'EXPENSES')");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var students = jdbc.queryForList("SELECT module FROM school_staff_modules WHERE staff_id=99001 ORDER BY module", String.class);
        assertEquals(8, students.size());
        assertTrue(students.containsAll(List.of("STUDENTS", "CALENDAR", "DISCIPLINE", "LIBRARY", "HOMEWORK", "REQUESTS", "PARENT_PORTAL", "MESSAGES")));
        assertEquals(List.of("CASH_BOOK", "EXPENSES", "FINANCIAL_STATEMENTS"), jdbc.queryForList("SELECT module FROM school_staff_modules WHERE staff_id=99002 ORDER BY module", String.class));
        jdbc.update("DELETE FROM school_staff_modules WHERE staff_id=99001");
        jdbc.update("INSERT INTO school_staff_modules(staff_id,module) VALUES(99001,'LIBRARY')");
        assertEquals(List.of("LIBRARY"), jdbc.queryForList("SELECT module FROM school_staff_modules WHERE staff_id=99001", String.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO school_staff_modules(staff_id,module) VALUES(99001,'UNKNOWN')"));
    }
}
