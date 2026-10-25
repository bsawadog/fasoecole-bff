package org.afritechinnovations.service.auth;

import org.afritechinnovations.dto.auth.AccountRecoveryDto.*;
import org.afritechinnovations.security.*;
import org.afritechinnovations.service.common.EmailService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses only an explicitly named disposable database, never a development school database. */
@EnabledIfSystemProperty(named="accountRecoveryTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/account_recovery_test")
class AccountRecoveryPostgresTest {
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackages="org.afritechinnovations.repository")
    @Import(AccountRecoveryService.class)
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setPackagesToScan("org.afritechinnovations.model"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate", "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
        @Bean EmailService mail() { return mock(EmailService.class); }
        @Bean AccessGuard guard() { return mock(AccessGuard.class); }
        @Bean AuthAttemptLimiter limiter() { return new AuthAttemptLimiter(); }
        @Bean static org.springframework.context.support.PropertySourcesPlaceholderConfigurer properties() {
            var config=new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
            var values=new Properties();values.setProperty("app.frontend.base-url","http://app");config.setProperties(values);return config;
        }
    }

    @Test void realTransactionsPreserveAccountUntilConfirmationAndInvalidateEveryOldLink() {
        var source=new DriverManagerDataSource(System.getProperty("accountRecoveryTestUrl"),"postgres","recovery-test-only");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var jdbc=new JdbcTemplate(source);
        // Reset only this test's fixture, so the disposable database can be reused for a rerun.
        jdbc.update("DELETE FROM account_recovery_events WHERE request_id IN (SELECT id FROM account_recovery_requests WHERE user_id=90002)");
        jdbc.update("DELETE FROM account_recovery_requests WHERE user_id=90002");
        jdbc.update("DELETE FROM email_verification_tokens WHERE user_id=90002");
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=90002");
        jdbc.update("DELETE FROM students WHERE id IN (90001,90002)");
        jdbc.update("DELETE FROM schools WHERE id IN (90001,90002)");
        jdbc.update("DELETE FROM users WHERE id IN (90001,90002)");
        jdbc.update("INSERT INTO users(id,first_name,last_name,email,password_hash,password_set,email_verified,approved) VALUES(90001,'Owner','Test','owner@example.test','hash',true,true,true),(90002,'Ali','Diallo','old@example.test','old-hash',true,true,true)");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id,status) VALUES(90001,'Recovery School','PRIMAIRE',90001,'ACTIVE'),(90002,'Second School','PRIMAIRE',90001,'ACTIVE')");
        jdbc.update("INSERT INTO students(id,user_id,school_id,first_name,last_name,registration_number) VALUES(90001,90002,90001,'Ali','Diallo','001'),(90002,90002,90002,'Ali','Diallo','002')");
        jdbc.update("INSERT INTO email_verification_tokens(user_id,token_hash,purpose,recipient_email,expires_at) VALUES(90002,repeat('a',64),'VERIFY','old@example.test',now()+interval '1 day')");
        jdbc.update("INSERT INTO password_reset_tokens(user_id,token_hash,recipient_email,expires_at) VALUES(90002,repeat('b',64),'old@example.test',now()+interval '1 day')");
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(DataSource.class,()->source);context.register(Config.class);context.refresh();
            var guard=context.getBean(AccessGuard.class);when(guard.currentUserId()).thenReturn(90001L);
            var mail=context.getBean(EmailService.class);var service=context.getBean(AccountRecoveryService.class);
            service.submit(new Submit(90001L,"001","Ali","Diallo","new@example.test","Courriel et mot de passe perdus"));
            Long id=jdbc.queryForObject("SELECT id FROM account_recovery_requests WHERE user_id=90002",Long.class);
            assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM account_recovery_requests WHERE id=?",String.class,id));
            assertEquals("old@example.test",jdbc.queryForObject("SELECT email FROM users WHERE id=90002",String.class));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE user_id=90002",Integer.class));
            service.decide(id,new Decision(true,true,"Responsable légal vérifié aux coordonnées enregistrées."));
            assertEquals("old@example.test",jdbc.queryForObject("SELECT email FROM users WHERE id=90002",String.class));
            var body=ArgumentCaptor.forClass(String.class);verify(mail).sendText(eq("new@example.test"),anyString(),body.capture());
            var matcher=java.util.regex.Pattern.compile("recover=([\\w-]+)").matcher(body.getValue());assertTrue(matcher.find());
            String token=matcher.group(1);
            service.complete(new Complete(token,"my-new-password"));
            assertEquals("new@example.test",jdbc.queryForObject("SELECT email FROM users WHERE id=90002",String.class));
            assertTrue(context.getBean(PasswordEncoder.class).matches("my-new-password",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=90002",String.class)));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM students WHERE user_id=90002",Integer.class));
            assertEquals("002",jdbc.queryForObject("SELECT registration_number FROM students WHERE id=90002",String.class));
            assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM account_recovery_requests WHERE id=?",String.class,id));
            assertEquals(1,jdbc.queryForObject("SELECT session_version FROM users WHERE id=90002",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE user_id=90002",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE user_id=90002",Integer.class));
            assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM account_recovery_events WHERE request_id=?",Integer.class,id));
            assertThrows(IllegalArgumentException.class,()->service.complete(new Complete(token,"another-password")));
        }
    }
}
