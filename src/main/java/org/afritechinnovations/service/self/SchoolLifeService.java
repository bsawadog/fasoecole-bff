package org.afritechinnovations.service.self;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SchoolLifeDto.*;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolLifeService {
    private final JdbcTemplate jdbc;
    private final AccessGuard guard;
    private final FamilySpaceService family;
    private final TeacherSpaceService teacher;

    private record Scope(Long schoolId, Long classId, Long studentId, boolean owner, boolean teacher) { }
    private Scope owner(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        guard.requireApprovedSchool(schoolId);
        return new Scope(schoolId, null, null, true, false);
    }
    private Scope teacher(Long classId) {
        var cls = teacher.requireTaughtClass(guard.currentUserId(), classId);
        guard.requireApprovedSchool(cls.getSchool().getId());
        return new Scope(cls.getSchool().getId(), classId, null, false, true);
    }
    private Scope student(Long studentId) {
        var child = family.requireGuardedChild(guard.currentUserId(), studentId);
        var detail = family.student(guard.currentUserId(), studentId);
        return new Scope(child.getSchool().getId(), detail.classId(), studentId, false, false);
    }
    public Overview ownerOverview(Long schoolId) { return ownerOverview(schoolId,null); }
    public Overview teacherOverview(Long classId) { return teacherOverview(classId,null); }
    public Overview studentOverview(Long studentId) { return studentOverview(studentId,null); }
    public Overview ownerOverview(Long schoolId, LifeModule module) { return overview(owner(schoolId),module); }
    public Overview teacherOverview(Long classId, LifeModule module) { return overview(teacher(classId),module); }
    public Overview studentOverview(Long studentId, LifeModule module) { return overview(student(studentId),module); }

    private Overview overview(Scope scope, LifeModule module) {
        Long schoolId = scope.schoolId(), classId = scope.classId(), studentId = scope.studentId();
        List<StudentChoice> students = jdbc.query("""
            SELECT DISTINCT s.id, concat(u.first_name,' ',u.last_name) AS name, c.id AS class_id, c.name AS class_name,
              u.last_name,u.first_name
            FROM students s JOIN users u ON u.id=s.user_id
            JOIN student_enrollments e ON e.student_id=s.id AND e.status='ACTIVE'
            JOIN classes c ON c.id=e.class_id AND c.school_id=s.school_id
            JOIN academic_years y ON y.id=e.academic_year_id AND y.id=c.academic_year_id AND y.is_current=true
            WHERE s.school_id=? AND (? OR c.id=?) AND (?::bigint IS NULL OR s.id=?)
            ORDER BY c.name,u.last_name,u.first_name,s.id
            """, (rs,n) -> new StudentChoice(rs.getLong("id"),rs.getString("name"),rs.getLong("class_id"),rs.getString("class_name")),
                schoolId,scope.owner(),classId,studentId,studentId);
        var events = module==null || module==LifeModule.CALENDAR ? jdbc.query("""
            SELECT e.*,c.name AS class_name FROM school_calendar_events e LEFT JOIN classes c ON c.id=e.class_id
            WHERE e.school_id=? AND (? OR e.class_id IS NULL OR e.class_id=?) ORDER BY e.starts_on,e.id
            """, (rs,n) -> new Event(rs.getLong("id"),rs.getObject("class_id",Long.class),rs.getString("class_name"),
                rs.getString("title"),rs.getString("description"),rs.getString("kind"),rs.getObject("starts_on",LocalDate.class),rs.getObject("ends_on",LocalDate.class)),
                schoolId,scope.owner(),classId) : List.<Event>of();
        var observations = module==null || module==LifeModule.DISCIPLINE ? jdbc.query("""
            SELECT o.*,concat(u.first_name,' ',u.last_name) AS student_name FROM student_observations o
            JOIN students s ON s.id=o.student_id JOIN users u ON u.id=s.user_id
            WHERE o.school_id=? AND (? OR o.class_id=?) AND (?::bigint IS NULL OR o.student_id=?)
              AND (? OR o.shared_with_family=true) ORDER BY o.observed_on DESC,o.id DESC
            """, (rs,n) -> new Observation(rs.getLong("id"),rs.getLong("student_id"),rs.getString("student_name"),rs.getString("kind"),
                rs.getObject("observed_on",LocalDate.class),rs.getString("description"),rs.getString("action"),rs.getBoolean("shared_with_family"),rs.getBoolean("resolved")),
                schoolId,scope.owner() || studentId != null,classId,studentId,studentId,scope.owner() || scope.teacher()) : List.<Observation>of();
        List<Document> requests = scope.teacher() || (module!=null && module!=LifeModule.REQUESTS) ? List.of() : jdbc.query("""
            SELECT r.*,concat(u.first_name,' ',u.last_name) AS student_name FROM school_document_requests r
            JOIN students s ON s.id=r.student_id JOIN users u ON u.id=s.user_id
            WHERE r.school_id=? AND (?::bigint IS NULL OR r.student_id=?) ORDER BY r.created_at DESC,r.id DESC
            """, (rs,n) -> new Document(rs.getLong("id"),rs.getLong("student_id"),rs.getString("student_name"),rs.getString("kind"),
                rs.getString("reason"),rs.getString("status"),rs.getString("response"),rs.getObject("created_at",LocalDateTime.class),
                Objects.equals(rs.getLong("requested_by"),guard.currentUserId())),schoolId,studentId,studentId);
        var books = module==null || module==LifeModule.LIBRARY ? jdbc.query("""
            SELECT b.*,b.copies-(SELECT count(*) FROM library_loans l WHERE l.book_id=b.id AND l.returned_on IS NULL) AS available
            FROM library_books b WHERE b.school_id=? ORDER BY b.title,b.id
            """, (rs,n) -> new Book(rs.getLong("id"),rs.getString("title"),rs.getString("author"),rs.getString("reference"),rs.getInt("copies"),rs.getInt("available")),schoolId) : List.<Book>of();
        var loans = module==null || module==LifeModule.LIBRARY ? jdbc.query("""
            SELECT l.*,b.title,concat(u.first_name,' ',u.last_name) AS student_name FROM library_loans l
            JOIN library_books b ON b.id=l.book_id JOIN students s ON s.id=l.student_id JOIN users u ON u.id=s.user_id
            WHERE l.school_id=? AND (?::bigint IS NULL OR l.student_id=?) AND (? OR EXISTS(
              SELECT 1 FROM student_enrollments e WHERE e.student_id=l.student_id AND e.class_id=? AND e.status='ACTIVE'))
            ORDER BY l.borrowed_on DESC,l.id DESC
            """, (rs,n) -> new Loan(rs.getLong("id"),rs.getLong("book_id"),rs.getString("title"),rs.getLong("student_id"),rs.getString("student_name"),
                rs.getObject("borrowed_on",LocalDate.class),rs.getObject("due_on",LocalDate.class),rs.getObject("returned_on",LocalDate.class)),
                schoolId,studentId,studentId,!scope.teacher(),classId) : List.<Loan>of();
        var homeworks = module==null || module==LifeModule.HOMEWORK ? jdbc.query("""
            SELECT DISTINCT p.id,p.title,p.content,p.due_date,p.author_id,s.id AS student_id,
              (y.is_current AND y.closed_at IS NULL) AS year_open,
              concat(u.first_name,' ',u.last_name) AS student_name,
              coalesce(h.status,'TO_DO') AS progress_status,coalesce(h.feedback,'') AS feedback
            FROM parent_portal_posts p JOIN classes c ON c.id=p.class_id AND c.school_id=p.school_id
            JOIN academic_years y ON y.id=c.academic_year_id
            JOIN student_enrollments e ON e.class_id=c.id AND e.academic_year_id=c.academic_year_id AND e.status='ACTIVE'
            JOIN students s ON s.id=e.student_id AND s.school_id=p.school_id JOIN users u ON u.id=s.user_id
            LEFT JOIN homework_progress h ON h.post_id=p.id AND h.student_id=s.id
            WHERE p.school_id=? AND p.kind='HOMEWORK' AND (? OR p.class_id=?)
              AND (?::bigint IS NULL OR s.id=?) AND (p.student_id IS NULL OR p.student_id=s.id)
            ORDER BY p.due_date DESC,p.id DESC,s.id
            """, (rs,n) -> new Homework(rs.getLong("id"),rs.getLong("student_id"),rs.getString("student_name"),rs.getString("title"),rs.getString("content"),
                rs.getObject("due_date",LocalDate.class),rs.getString("progress_status"),rs.getString("feedback"),
                rs.getBoolean("year_open") && (scope.owner() || (scope.teacher() && rs.getLong("author_id")==guard.currentUserId()))),schoolId,scope.owner(),classId,studentId,studentId) : List.<Homework>of();
        return new Overview(students,events,observations,requests,books,loans,homeworks);
    }

    @Transactional
    public void createEvent(Long schoolId, EventRequest request) {
        owner(schoolId);
        if (request.endsOn().isBefore(request.startsOn())) throw new IllegalArgumentException("La fin précède le début");
        if (request.classId()!=null) guard.requireSame(schoolId,guard.schoolOfClass(request.classId()),"classe");
        jdbc.update("INSERT INTO school_calendar_events(school_id,class_id,title,description,kind,starts_on,ends_on,created_by) VALUES(?,?,?,?,?,?,?,?)",
                schoolId,request.classId(),request.title().trim(),request.description().trim(),request.kind().name(),request.startsOn(),request.endsOn(),guard.currentUserId());
    }
    @Transactional
    public void deleteEvent(Long schoolId, Long id) {
        owner(schoolId);
        changed(jdbc.update("DELETE FROM school_calendar_events WHERE id=? AND school_id=?",id,schoolId));
    }
    @Transactional
    public void ownerObservation(Long schoolId, ObservationRequest request) { createObservation(owner(schoolId),request); }
    @Transactional
    public void teacherObservation(Long classId, ObservationRequest request) { createObservation(teacher(classId),request); }
    private void createObservation(Scope scope, ObservationRequest request) {
        if (scope.teacher() && !Objects.equals(scope.classId(),request.classId())) throw new AccessDeniedException("Choisissez votre classe");
        requireEnrollment(scope.schoolId(),request.classId(),request.studentId());
        jdbc.update("INSERT INTO student_observations(school_id,class_id,student_id,kind,observed_on,description,action,shared_with_family,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                scope.schoolId(),request.classId(),request.studentId(),request.kind().name(),request.observedOn(),request.description().trim(),request.action().trim(),request.sharedWithFamily(),guard.currentUserId());
    }
    @Transactional
    public void resolveObservation(Long schoolId, Long id, ObservationDecision request) {
        owner(schoolId);
        changed(jdbc.update("UPDATE student_observations SET action=?,shared_with_family=?,resolved=? WHERE id=? AND school_id=?",
                request.action().trim(),request.sharedWithFamily(),request.resolved(),id,schoolId));
    }
    private void requireEnrollment(Long schoolId, Long classId, Long studentId) {
        Boolean enrolled = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM students s JOIN student_enrollments e ON e.student_id=s.id
            JOIN classes c ON c.id=e.class_id JOIN academic_years y ON y.id=e.academic_year_id
            WHERE s.id=? AND s.school_id=? AND c.id=? AND c.school_id=s.school_id AND e.status='ACTIVE'
              AND y.id=c.academic_year_id AND y.is_current=true AND y.closed_at IS NULL)
            """,Boolean.class,studentId,schoolId,classId);
        if (!Boolean.TRUE.equals(enrolled)) throw new AccessDeniedException("L'élève n'est pas inscrit dans cette classe pour l'année en cours");
    }
    @Transactional
    public void requestDocument(Long studentId, DocumentRequest request) {
        var scope=student(studentId);
        jdbc.update("INSERT INTO school_document_requests(school_id,student_id,requested_by,kind,reason) VALUES(?,?,?,?,?)",
                scope.schoolId(),studentId,guard.currentUserId(),request.kind().name(),request.reason().trim());
    }
    @Transactional
    public void decideDocument(Long schoolId, Long id, DocumentDecision request) {
        owner(schoolId);
        changed(jdbc.update("UPDATE school_document_requests SET status=?,response=?,handled_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND school_id=? AND status IN ('PENDING','IN_PROGRESS')",
                request.status().name(),request.response().trim(),guard.currentUserId(),id,schoolId));
    }
    @Transactional
    public void cancelDocument(Long studentId, Long id) {
        var scope=student(studentId);
        changed(jdbc.update("UPDATE school_document_requests SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND school_id=? AND student_id=? AND requested_by=? AND status='PENDING'",
                id,scope.schoolId(),studentId,guard.currentUserId()));
    }
    @Transactional
    public void createBook(Long schoolId, BookRequest request) {
        owner(schoolId);
        jdbc.update("INSERT INTO library_books(school_id,title,author,reference,copies,created_by) VALUES(?,?,?,?,?,?)",
                schoolId,request.title().trim(),request.author().trim(),request.reference().trim(),request.copies(),guard.currentUserId());
    }
    @Transactional
    public void lend(Long schoolId, LoanRequest request) {
        owner(schoolId);
        guard.requireSame(schoolId,guard.schoolOfStudent(request.studentId()),"élève");
        if (request.dueOn().isBefore(LocalDate.now())) throw new IllegalArgumentException("La date de retour est passée");
        // Serialize issuance on the book row so simultaneous requests cannot exceed the stock.
        var stock=jdbc.query("SELECT copies FROM library_books WHERE id=? AND school_id=? FOR UPDATE",(rs,n)->rs.getInt(1),request.bookId(),schoolId);
        if (stock.isEmpty()) throw new AccessDeniedException("Livre inaccessible");
        Long borrowed=jdbc.queryForObject("SELECT count(*) FROM library_loans WHERE book_id=? AND returned_on IS NULL",Long.class,request.bookId());
        if (borrowed>=stock.getFirst()) throw new IllegalArgumentException("Aucun exemplaire disponible");
        jdbc.update("INSERT INTO library_loans(school_id,book_id,student_id,due_on,issued_by) VALUES(?,?,?,?,?)",
                schoolId,request.bookId(),request.studentId(),request.dueOn(),guard.currentUserId());
    }
    @Transactional
    public void returnBook(Long schoolId, Long loanId) {
        owner(schoolId);
        changed(jdbc.update("UPDATE library_loans SET returned_on=CURRENT_DATE,returned_by=? WHERE id=? AND school_id=? AND returned_on IS NULL",guard.currentUserId(),loanId,schoolId));
    }
    @Transactional
    public void ownerHomework(Long schoolId, Long postId, Long studentId, HomeworkDecision request) {
        updateHomework(owner(schoolId),postId,studentId,request);
    }
    @Transactional
    public void teacherHomework(Long classId, Long postId, Long studentId, HomeworkDecision request) {
        updateHomework(teacher(classId),postId,studentId,request);
    }
    private void updateHomework(Scope scope, Long postId, Long studentId, HomeworkDecision request) {
        var classes=jdbc.query("SELECT class_id FROM parent_portal_posts WHERE id=? AND school_id=? AND kind='HOMEWORK' AND (student_id IS NULL OR student_id=?) AND (? OR (author_id=? AND class_id=?))",
                (rs,n)->rs.getLong(1),postId,scope.schoolId(),studentId,scope.owner(),guard.currentUserId(),scope.classId());
        if (classes.isEmpty()) throw new AccessDeniedException("Vous ne pouvez corriger que vos propres devoirs");
        requireEnrollment(scope.schoolId(),classes.getFirst(),studentId);
        jdbc.update("""
            INSERT INTO homework_progress(school_id,post_id,student_id,status,feedback,updated_by) VALUES(?,?,?,?,?,?)
            ON CONFLICT(post_id,student_id) DO UPDATE SET status=EXCLUDED.status,feedback=EXCLUDED.feedback,
            updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP
            """,scope.schoolId(),postId,studentId,request.status().name(),request.feedback().trim(),guard.currentUserId());
    }
    private void changed(int count) {
        if (count==0) throw new IllegalArgumentException("Élément introuvable ou déjà traité");
    }
}
