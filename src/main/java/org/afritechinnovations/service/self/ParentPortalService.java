package org.afritechinnovations.service.self;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.ParentPortalDto.*;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentPortalService {
    private final JdbcTemplate jdbc;
    private final AccessGuard guard;
    private final FamilySpaceService family;
    private final ConversationMessagingService messaging;
    private final TeacherSpaceService teacher;

    private static final String POST_SELECT = "SELECT p.*, c.name AS class_name FROM parent_portal_posts p LEFT JOIN classes c ON c.id=p.class_id ";
    private static final String APPOINTMENT_SELECT = """
        SELECT a.*, concat(su.first_name, ' ', su.last_name) AS student_name,
               concat(pu.first_name, ' ', pu.last_name) AS parent_name,
               CASE WHEN tu.id IS NULL THEN 'Administration' ELSE concat(tu.first_name, ' ', tu.last_name) END AS teacher_name
        FROM parent_appointments a JOIN students s ON s.id=a.student_id JOIN users su ON su.id=s.user_id
        JOIN users pu ON pu.id=a.parent_user_id LEFT JOIN users tu ON tu.id=a.teacher_user_id
        """;

    public List<Post> studentPosts(Long studentId, Kind kind) {
        var child = family.requireGuardedChild(guard.currentUserId(), studentId);
        var overview = family.student(guard.currentUserId(), studentId);
        return jdbc.query(POST_SELECT + "WHERE p.school_id=? AND p.kind=? AND (p.student_id=? OR (p.student_id IS NULL AND (p.class_id IS NULL OR p.class_id=?))) ORDER BY p.created_at DESC, p.id DESC",
                this::post, child.getSchool().getId(), kind.name(), studentId, overview.classId());
    }

    public List<Post> schoolPosts(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return jdbc.query(POST_SELECT + "WHERE p.school_id=? ORDER BY p.created_at DESC, p.id DESC", this::post, schoolId);
    }

    @Transactional
    public Post publish(Long schoolId, PostRequest request, List<MultipartFile> files) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return storePost(schoolId,request,files);
    }

    private Post storePost(Long schoolId, PostRequest request, List<MultipartFile> files) {
        if (request.classId() != null) guard.requireSame(schoolId, guard.schoolOfClass(request.classId()), "classe");
        if (request.studentId() != null) {
            if (request.classId() == null) throw new IllegalArgumentException("Choisissez la classe de l'enfant");
            Boolean linked = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM students s JOIN student_enrollments e ON e.student_id=s.id WHERE s.id=? AND s.school_id=? AND e.class_id=?)", Boolean.class,
                    request.studentId(), schoolId, request.classId());
            if (!Boolean.TRUE.equals(linked)) throw new IllegalArgumentException("Cet enfant n'est pas inscrit dans cette classe");
        }
        if (request.kind() == Kind.HOMEWORK && request.classId() == null) throw new IllegalArgumentException("Choisissez une classe pour le devoir");
        if (request.kind() == Kind.HOMEWORK && request.dueDate() == null) throw new IllegalArgumentException("Indiquez la date de remise du devoir");
        if (request.dueDate() != null && request.dueDate().isBefore(LocalDate.now())) throw new IllegalArgumentException("La date ne doit pas être passée");
        List<Upload> uploads = prepare(files);
        if (request.kind() == Kind.DOCUMENT && uploads.isEmpty()) throw new IllegalArgumentException("Ajoutez au moins un document");
        Long id = jdbc.queryForObject("INSERT INTO parent_portal_posts(school_id,class_id,student_id,author_id,kind,title,content,due_date) VALUES (?,?,?,?,?,?,?,?) RETURNING id",
                Long.class, schoolId, request.classId(), request.studentId(), guard.currentUserId(), request.kind().name(), request.title().trim(), request.content().trim(), request.dueDate());
        for (Upload upload : uploads) jdbc.update("INSERT INTO parent_portal_files(post_id,filename,size_bytes,data) VALUES (?,?,?,?)", id, upload.name(), upload.data().length, upload.data());
        return jdbc.queryForObject(POST_SELECT + "WHERE p.id=?", this::post, id);
    }

    public List<Post> teacherPosts(Long classId, Kind kind) {
        var schoolClass = teacher.requireTaughtClass(guard.currentUserId(),classId);
        return jdbc.query(POST_SELECT + "WHERE p.school_id=? AND p.kind=? AND (p.class_id=? OR p.class_id IS NULL) AND (p.student_id IS NULL OR p.author_id=?) ORDER BY p.created_at DESC,p.id DESC",
                this::post,schoolClass.getSchool().getId(),kind.name(),classId,guard.currentUserId());
    }

    @Transactional
    public Post teacherPublish(Long classId, PostRequest request, List<MultipartFile> files) {
        var schoolClass = teacher.requireTaughtClass(guard.currentUserId(),classId);
        if (!Objects.equals(request.classId(),classId)) throw new AccessDeniedException("Publiez uniquement dans votre classe");
        return storePost(schoolClass.getSchool().getId(),request,files);
    }

    public Download teacherDownload(Long classId, Long postId, Long fileId) {
        var schoolClass = teacher.requireTaughtClass(guard.currentUserId(),classId);
        return download("p.school_id=? AND (p.class_id=? OR p.class_id IS NULL) AND (p.student_id IS NULL OR p.author_id=?)",postId,fileId,schoolClass.getSchool().getId(),classId,guard.currentUserId());
    }

    @Transactional
    public void teacherDelete(Long classId, Long id) {
        teacher.requireTaughtClass(guard.currentUserId(),classId);
        if (jdbc.update("DELETE FROM parent_portal_posts WHERE id=? AND class_id=? AND author_id=?",id,classId,guard.currentUserId()) == 0)
            throw new AccessDeniedException("Vous ne pouvez retirer que vos publications de cette classe");
    }

    public List<Appointment> teacherAppointments(Long classId) {
        teacher.requireTaughtClass(guard.currentUserId(),classId);
        Set<Long> studentIds = teacher.students(guard.currentUserId(),classId).stream().map(s -> s.studentId()).collect(java.util.stream.Collectors.toSet());
        return jdbc.query(APPOINTMENT_SELECT + " WHERE a.teacher_user_id=? ORDER BY a.proposed_at DESC",this::appointment,guard.currentUserId()).stream()
                .filter(a -> studentIds.contains(a.studentId())).toList();
    }

    @Transactional
    public void teacherDecide(Long classId, Long id, AppointmentDecision request) {
        if (teacherAppointments(classId).stream().noneMatch(a -> a.id().equals(id))) throw new AccessDeniedException("Cette demande ne vous est pas destinée");
        int updated = jdbc.update("UPDATE parent_appointments SET status=?,response=? WHERE id=? AND teacher_user_id=? AND status='PENDING' AND (?='REJECTED' OR proposed_at>?)",
                request.status().name(),request.response().trim(),id,guard.currentUserId(),request.status().name(),LocalDateTime.now());
        if (updated == 0) throw new IllegalArgumentException("La demande a déjà été traitée ou sa date est passée");
    }

    @Transactional
    public void deletePost(Long schoolId, Long postId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        if (jdbc.update("DELETE FROM parent_portal_posts WHERE id=? AND school_id=?", postId, schoolId) == 0) throw new IllegalArgumentException("Publication introuvable");
    }

    public Download studentDownload(Long studentId, Long postId, Long fileId) {
        var child = family.requireGuardedChild(guard.currentUserId(), studentId);
        var overview = family.student(guard.currentUserId(), studentId);
        return download("p.school_id=? AND (p.student_id=? OR (p.student_id IS NULL AND (p.class_id IS NULL OR p.class_id=?)))", postId, fileId, child.getSchool().getId(), studentId, overview.classId());
    }

    public Download schoolDownload(Long schoolId, Long postId, Long fileId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return download("p.school_id=?", postId, fileId, schoolId);
    }

    private Download download(String condition, Long postId, Long fileId, Object... scope) {
        List<Object> args = new ArrayList<>(List.of(postId, fileId));
        Collections.addAll(args, scope);
        var results = jdbc.query("SELECT f.filename,f.data FROM parent_portal_files f JOIN parent_portal_posts p ON p.id=f.post_id WHERE p.id=? AND f.id=? AND " + condition,
                (rs,n) -> new Download(rs.getString("filename"), rs.getBytes("data")), args.toArray());
        if (results.isEmpty()) throw new AccessDeniedException("Document inaccessible");
        return results.getFirst();
    }

    public List<PaymentItem> payments(Long studentId) {
        family.requireGuardedChild(guard.currentUserId(), studentId);
        return jdbc.query("SELECT p.*,f.name AS fee_name FROM payments p JOIN invoices i ON i.id=p.invoice_id JOIN fee_types f ON f.id=i.fee_type_id WHERE i.student_id=? ORDER BY p.payment_date DESC,p.id DESC",
                (rs,n) -> new PaymentItem(rs.getLong("id"),rs.getString("fee_name"),rs.getBigDecimal("amount"),rs.getObject("payment_date",LocalDate.class),rs.getString("method"),rs.getString("reference")), studentId);
    }

    public List<EvaluationItem> evaluations(Long studentId) {
        family.requireGuardedChild(guard.currentUserId(), studentId);
        Long classId = family.student(guard.currentUserId(), studentId).classId();
        if (classId == null) return List.of();
        return jdbc.query("SELECT e.id,e.title,e.type,e.eval_date,s.name AS subject_name FROM evaluations e JOIN class_subject_teacher c ON c.id=e.class_subject_teacher_id JOIN subjects s ON s.id=c.subject_id WHERE c.class_id=? ORDER BY e.eval_date DESC,e.id DESC",
                (rs,n) -> new EvaluationItem(rs.getLong("id"),rs.getString("title"),rs.getString("subject_name"),rs.getString("type"),rs.getObject("eval_date",LocalDate.class)), classId);
    }

    public List<Appointment> studentAppointments(Long studentId) {
        family.requireGuardedChild(guard.currentUserId(), studentId);
        return jdbc.query(APPOINTMENT_SELECT + " WHERE a.student_id=? AND a.parent_user_id=? ORDER BY a.proposed_at DESC", this::appointment, studentId, guard.currentUserId());
    }

    public List<Appointment> schoolAppointments(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return jdbc.query(APPOINTMENT_SELECT + " WHERE a.school_id=? ORDER BY a.proposed_at DESC", this::appointment, schoolId);
    }

    @Transactional
    public Appointment requestAppointment(Long studentId, AppointmentRequest request) {
        var child = family.requireGuardedChild(guard.currentUserId(), studentId);
        if (request.teacherUserId() != null && messaging.recipients(guard.currentUserId(), child.getSchool().getId(), studentId).stream()
                .noneMatch(r -> r.userId().equals(request.teacherUserId()) && r.role().equals("ENSEIGNANT")))
            throw new AccessDeniedException("Cet enseignant n'est pas affecté à cet enfant");
        Long id = jdbc.queryForObject("INSERT INTO parent_appointments(school_id,student_id,parent_user_id,teacher_user_id,proposed_at,reason) VALUES (?,?,?,?,?,?) RETURNING id", Long.class,
                child.getSchool().getId(),studentId,guard.currentUserId(),request.teacherUserId(),request.proposedAt(),request.reason().trim());
        return jdbc.queryForObject(APPOINTMENT_SELECT + " WHERE a.id=?", this::appointment, id);
    }

    @Transactional
    public void cancelAppointment(Long studentId, Long id) {
        family.requireGuardedChild(guard.currentUserId(), studentId);
        int updated = jdbc.update("UPDATE parent_appointments SET status='CANCELLED' WHERE id=? AND student_id=? AND parent_user_id=? AND status IN ('PENDING','ACCEPTED')", id,studentId,guard.currentUserId());
        if (updated == 0) throw new IllegalArgumentException("Ce rendez-vous ne peut plus être annulé");
    }

    @Transactional
    public void decideAppointment(Long schoolId, Long id, AppointmentDecision request) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        int updated = jdbc.update("UPDATE parent_appointments SET status=?,response=? WHERE id=? AND school_id=? AND status='PENDING' AND (?='REJECTED' OR proposed_at> ?)",
                request.status().name(),request.response().trim(),id,schoolId,request.status().name(),LocalDateTime.now());
        if (updated == 0) throw new IllegalArgumentException("La demande a déjà été traitée ou sa date est passée");
    }

    private Post post(ResultSet rs, int n) throws SQLException {
        Long id = rs.getLong("id");
        var files = jdbc.query("SELECT id,filename,size_bytes FROM parent_portal_files WHERE post_id=? ORDER BY id", (f,i) -> new FileItem(f.getLong("id"),f.getString("filename"),f.getLong("size_bytes")), id);
        return new Post(id,Kind.valueOf(rs.getString("kind")),rs.getObject("class_id",Long.class),rs.getString("class_name"),rs.getObject("student_id",Long.class),rs.getString("title"),rs.getString("content"),rs.getObject("due_date",LocalDate.class),rs.getObject("created_at",LocalDateTime.class),files,Objects.equals(rs.getLong("author_id"),guard.currentUserId()));
    }
    private Appointment appointment(ResultSet rs,int n) throws SQLException {
        return new Appointment(rs.getLong("id"),rs.getLong("student_id"),rs.getString("student_name"),rs.getString("parent_name"),rs.getString("teacher_name"),rs.getObject("proposed_at",LocalDateTime.class),rs.getString("reason"),rs.getString("status"),rs.getString("response"));
    }
    private record Upload(String name, byte[] data) {}
    private List<Upload> prepare(List<MultipartFile> files) {
        if (files == null) return List.of();
        if (files.size()>3) throw new IllegalArgumentException("Maximum 3 pièces jointes");
        Set<String> allowed = Set.of("pdf","doc","docx","xls","xlsx","ppt","pptx","odt","ods","odp","txt","csv","jpg","jpeg","png");
        List<Upload> result = new ArrayList<>();
        for (var file : files) {
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\','/');
            name = name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "").trim();
            int dot = name.lastIndexOf('.');
            if (file.isEmpty() || file.getSize()>10*1024*1024 || name.length()>200 || dot<1 || !allowed.contains(name.substring(dot+1).toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Fichier invalide : formats bureautiques ou images, 10 Mo maximum");
            try { result.add(new Upload(name,file.getBytes())); }
            catch (IOException ex) { throw new IllegalArgumentException("Impossible de lire le fichier",ex); }
        }
        return result;
    }
}
