package org.afritechinnovations.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.afritechinnovations.dto.finance.SchoolResourcesDto.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="resourcesTestUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/resources_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchoolResourcesIntegrationTest {
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    SchoolResourcesService service;
    SchoolRepository schools;
    final long school = 99001L, owner = 99001L;
    @BeforeAll void initialize() {
        var source = new DriverManagerDataSource(System.getProperty("resourcesTestUrl"),"postgres","disposable-test-only");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source); transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update("INSERT INTO users(id,first_name,last_name,password_hash) VALUES(99001,'Owner','Test','hash'),(99002,'Other','Test','hash')");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(99001,'One','PRIMAIRE',99001),(99002,'Other','PRIMAIRE',99002)");
        schools = mock(SchoolRepository.class);
        when(schools.findById(school)).thenReturn(Optional.of(School.builder().id(school).owner(User.builder().id(owner).build()).status(SchoolStatus.ACTIVE).build()));
        when(schools.findById(99002L)).thenReturn(Optional.of(School.builder().id(99002L).owner(User.builder().id(99002L).build()).status(SchoolStatus.ACTIVE).build()));
        service = new SchoolResourcesService(jdbc,schools,mock(SchoolPermissions.class),new ObjectMapper().findAndRegisterModules());
    }
    <T> T tx(Supplier<T> action) { return transaction.execute(status -> action.get()); }
    StockInput input(String code, boolean active, String unit, Long version) {
        return new StockInput(code,"Craie "+code,StockCategory.TEACHING,unit,"Magasin",new BigDecimal("2"),new BigDecimal("500"),active,"",version);
    }
    StockItem item(String code) { return tx(() -> service.saveStockItem(school,null,input(code,true,"boîte",null),owner,false)); }
    MovementInput movement(MovementKind kind, String quantity) {
        return new MovementInput(UUID.randomUUID(),kind,LocalDate.now(),new BigDecimal(quantity),"Test de mouvement","CP1","BON-1");
    }
    StockItem move(StockItem item, MovementInput request) { return tx(() -> service.move(school,item.id(),request,owner,false)); }

    @Test void stockJournalIsIdempotentRejectsOverdrawAndPreservesPhysicalCounts() {
        var item = item("CHALK-A");
        var receipt = movement(MovementKind.IN,"10.125");
        assertEquals(0, move(item,receipt).quantity().compareTo(new BigDecimal("10.125")));
        move(item,receipt);
        assertEquals(1, service.movements(school,item.id(),owner,false).size());
        var issue = movement(MovementKind.OUT,"3.125"); move(item,issue);
        assertThrows(IllegalArgumentException.class, () -> move(item,movement(MovementKind.OUT,"8")));
        assertEquals(2, service.movements(school,item.id(),owner,false).size());
        var counted = move(item,movement(MovementKind.COUNT,"5"));
        assertEquals(0, counted.quantity().compareTo(new BigDecimal("5")));
        var ledger = service.movements(school,item.id(),owner,false);
        assertEquals(0, ledger.getFirst().quantityChange().compareTo(new BigDecimal("-2")));
        assertEquals(3, ledger.size());
        var conflictingRetry = new MovementInput(receipt.requestId(),receipt.kind(),receipt.date(),BigDecimal.ONE,"Different","CP1","BON-1");
        assertThrows(IllegalArgumentException.class, () -> move(item,conflictingRetry));
    }

    @Test void preventsCrossSchoolIdsUnitChangesAndArchivingRemainingStock() {
        var item = item("CHALK-B");
        var current = move(item,movement(MovementKind.IN,"3"));
        assertThrows(IllegalArgumentException.class, () -> tx(() -> service.saveStockItem(school,item.id(),input(item.code(),false,"boîte",current.version()),owner,false)));
        assertThrows(IllegalArgumentException.class, () -> tx(() -> service.saveStockItem(school,item.id(),input(item.code(),true,"pièce",current.version()),owner,false)));
        assertThrows(IllegalArgumentException.class, () -> tx(() -> service.move(99002L,item.id(),movement(MovementKind.IN,"1"),99002L,false)));
        assertThrows(AccessDeniedException.class, () -> service.stock(school,99002L,false));
        var empty = move(item,movement(MovementKind.COUNT,"0"));
        var archived = tx(() -> service.saveStockItem(school,item.id(),input(item.code(),false,"boîte",empty.version()),owner,false));
        assertFalse(archived.active());
        assertThrows(IllegalArgumentException.class, () -> move(item,movement(MovementKind.IN,"1")));
        assertEquals(2,service.movements(school,item.id(),owner,false).size());
    }

    @Test void concurrentIssuesCannotConsumeTheSameStockTwice() throws Exception {
        var item = item("CHALK-C"); move(item,movement(MovementKind.IN,"5"));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> issue = () -> { start.await(); try { move(item,movement(MovementKind.OUT,"4")); return true; } catch (IllegalArgumentException expected) { return false; } };
            var first = executor.submit(issue); var second = executor.submit(issue); start.countDown();
            assertNotEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
        }
        assertEquals(0, service.stock(school,owner,false).items().stream().filter(row -> row.id().equals(item.id())).findFirst().orElseThrow().quantity().compareTo(BigDecimal.ONE));
        assertEquals(2, service.movements(school,item.id(),owner,false).size());
    }

    AssetInput assetInput(Long version, String location, AssetStatus status, String reason) {
        return new AssetInput("MOB-1","Tables-bancs CM1",AssetCategory.DESK_BENCH,location,"Titulaire",Ownership.OWNED,
                AssetCondition.GOOD,status,20,new BigDecimal("15000"),LocalDate.now(),null,"",version,reason);
    }
    @Test void assetsKeepTheirHistoryAndRejectStaleOrCrossSchoolUpdates() throws Exception {
        var asset = tx(() -> service.saveAsset(school,null,assetInput(null,"CM1",AssetStatus.IN_USE,""),owner,false));
        var edited = tx(() -> service.saveAsset(school,asset.id(),assetInput(asset.version(),"Réserve",AssetStatus.STORED,"Déplacement"),owner,false));
        assertEquals(1,edited.version());
        assertThrows(IllegalArgumentException.class, () -> tx(() -> service.saveAsset(school,asset.id(),assetInput(asset.version(),"CM2",AssetStatus.IN_USE,"Autre déplacement"),owner,false)));
        assertThrows(IllegalArgumentException.class, () -> tx(() -> service.saveAsset(99002L,asset.id(),assetInput(edited.version(),"Autre école",AssetStatus.IN_USE,"Transfert"),99002L,false)));
        tx(() -> service.saveAsset(school,asset.id(),assetInput(edited.version(),"Réserve",AssetStatus.RETIRED,"Mobilier réformé"),owner,false));
        var events = service.assetHistory(school,asset.id(),owner,false);
        assertEquals(3,events.size()); assertTrue(events.getFirst().snapshot().contains("RETIRED"));
        assertEquals("CM1",new ObjectMapper().readTree(events.getLast().snapshot()).get("location").asText());
        assertEquals(AssetStatus.RETIRED,service.assets(school,owner,false).assets().getFirst().status());
        assertThrows(IllegalArgumentException.class, () -> service.assetHistory(99002L,asset.id(),99002L,false));
    }

    @Test void completeSchoolExportIncludesTheRegistersAndTheirHistory() throws Exception {
        var guard = mock(org.afritechinnovations.security.AccessGuard.class);
        when(guard.currentUserId()).thenReturn(owner);
        var exporter = new org.afritechinnovations.service.export.SchoolExportService(schools,jdbc,guard);
        var file = tx(() -> {
            try { return exporter.create(school,false); }
            catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        try (var zip = new java.util.zip.ZipFile(file.path().toFile())) {
            String workbook = new String(zip.getInputStream(zip.getEntry("xl/workbook.xml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(workbook.contains("Patrimoine et mobilier"));
            assertTrue(workbook.contains("Historique patrimoine"));
            assertTrue(workbook.contains("Articles de stock"));
            assertTrue(workbook.contains("Mouvements stock"));
        } finally { java.nio.file.Files.deleteIfExists(file.path()); }
    }
}
