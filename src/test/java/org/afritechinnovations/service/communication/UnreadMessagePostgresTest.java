package org.afritechinnovations.service.communication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="dashboardChartTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/dashboard_chart_test")
class UnreadMessagePostgresTest {
 SingleConnectionDataSource source;JdbcTemplate jdbc;
 @BeforeEach void setup(){
  source=new SingleConnectionDataSource(System.getProperty("dashboardChartTestUrl"),"chart_test","chart_test_only",true);jdbc=new JdbcTemplate(source);
  jdbc.execute("CREATE TEMP TABLE school_conversations(id BIGINT,school_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE conversation_participants(conversation_id BIGINT,school_id BIGINT,user_id BIGINT,last_read_at TIMESTAMP)");
  jdbc.execute("CREATE TEMP TABLE school_conversation_messages(id BIGINT,conversation_id BIGINT,sender_id BIGINT,from_school BOOLEAN,sent_at TIMESTAMP)");
  jdbc.update("INSERT INTO school_conversations VALUES(1,10),(2,10),(3,20),(4,10)");
  jdbc.update("INSERT INTO conversation_participants VALUES(1,null,100,null),(1,null,200,'2026-10-18 10:00'),(2,10,null,null),(3,null,100,null),(4,null,300,null)");
  jdbc.update("INSERT INTO school_conversation_messages VALUES(1,1,200,false,'2026-10-18 10:00'),(2,2,200,false,'2026-10-18 10:00'),(3,3,200,false,'2026-10-18 10:00'),(4,4,200,false,'2026-10-18 10:00')");
 }
 @AfterEach void close(){source.destroy();}
 long count(long school,long user){return jdbc.queryForObject("WITH params AS (SELECT ?::bigint school_id,?::bigint user_id) "+UnreadMessageSql.SCHOOL_BODY,Long.class,school,user);}
 @Test void includesTeacherToOwnerPersonalMessagesAndSharedInboxButNoOtherPrivateThread(){assertEquals(2,count(10,100));assertEquals(1,count(10,999));assertEquals(1,count(20,100));}
 @Test void readingOneParticipantDoesNotReadAnothersMessagesAndNewRepliesBecomeUnread(){
  jdbc.update("UPDATE conversation_participants SET last_read_at='2026-10-18 10:00' WHERE user_id=100");assertEquals(1,count(10,100));
  jdbc.update("INSERT INTO school_conversation_messages VALUES(5,1,200,false,'2026-10-18 10:01')");assertEquals(2,count(10,100));
  assertEquals(0,count(10,200));
 }
 @Test void personalParticipantTakesPrecedenceWithoutDoubleCountingSharedInbox(){
  jdbc.update("INSERT INTO conversation_participants VALUES(1,10,null,null)");assertEquals(2,count(10,100));
  jdbc.update("UPDATE conversation_participants SET last_read_at='2026-10-18 10:00' WHERE user_id=100");assertEquals(1,count(10,100));
  jdbc.update("INSERT INTO school_conversation_messages VALUES(5,2,100,false,'2026-10-18 10:01')");assertEquals(1,count(10,100));
 }
 @Test void sharedInboxReadDoesNotHideDirectPersonalDelivery(){
  jdbc.update("UPDATE conversation_participants SET last_read_at='2026-10-18 10:00' WHERE school_id=10");assertEquals(1,count(10,100));assertEquals(0,count(10,999));
 }
}
