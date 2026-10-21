package org.afritechinnovations.service.self;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.service.academic.SelectedAcademicYear;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class TeacherDailyToolsService {
    private final JdbcTemplate jdbc;
    private final TeacherSpaceService teacher;
    private final FamilySpaceService family;
    private final TeacherRepository teachers;
    private final ConversationMessagingService messages;

    public record LessonRequest(@NotNull Long subjectId,@NotNull LocalDate lessonOn,@NotBlank @Size(max=160) String title,
        @NotBlank @Size(max=4000) String content,@NotNull @Size(max=2000) String objectives,@NotNull @Size(max=2000) String nextWork,boolean shared) {}
    public record Lesson(Long id,Long subjectId,String subjectName,LocalDate lessonOn,String title,String content,String objectives,String nextWork,boolean shared) {}
    public record NoteRequest(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=4000) String content,LocalDate remindOn,boolean done) {}
    public record Note(Long id,String title,String content,LocalDate remindOn,boolean done) {}
    public record SupportRequest(@NotBlank @Size(max=4000) String action,LocalDate reviewOn,boolean resolved,
        @NotNull @Size(max=2000) String familyMessage,boolean shared) {}
    public record Support(Long studentId,String studentName,long absences,long overdue,BigDecimal average,Long id,String action,LocalDate reviewOn,boolean resolved,String familyMessage,boolean shared) {}
    public record FamilySupport(String teacherName,String message,LocalDate reviewOn,boolean resolved) {}
    public record FamilyLearning(List<Lesson> lessons,List<FamilySupport> support) {}
    public record AvailabilityRequest(@NotNull Long teacherId,@NotNull LocalDateTime startsAt,@NotNull LocalDateTime endsAt,
        @NotBlank @Size(max=2000) String reason,boolean replacementRequested) {}
    public record Availability(Long id,String schoolName,LocalDateTime startsAt,LocalDateTime endsAt,String reason,boolean replacementRequested,Long conversationId) {}
    public record Task(String title,long count,String route) {}
    public record Today(LocalDate date,List<SelfServiceDto.ScheduleEntry> lessons,List<Task> tasks,List<Note> reminders) {}

    private void requireTeacher(Long user) {
        if(teachers.findByUserId(user).isEmpty())throw new AccessDeniedException("Espace réservé aux enseignants.");
    }
    private SchoolClass scope(Long user,Long classId,boolean write) {
        var cls=teacher.requireTaughtClass(user,classId);
        if(cls.getSchool().getStatus()!=SchoolStatus.ACTIVE)throw new AccessDeniedException("Établissement inactif.");
        if(write && (!Boolean.TRUE.equals(cls.getAcademicYear().getIsCurrent()) || cls.getAcademicYear().isClosed()))
            throw new IllegalArgumentException("Cette année scolaire n’est plus ouverte à la saisie.");
        return cls;
    }
    private final org.springframework.jdbc.core.RowMapper<Lesson> lessonMapper=(r,n)->new Lesson(r.getLong("id"),r.getLong("subject_id"),r.getString("subject_name"),r.getObject("lesson_on",LocalDate.class),r.getString("title"),r.getString("content"),r.getString("objectives"),r.getString("next_work"),r.getBoolean("shared"));
    private static final String LESSON_SELECT="SELECT l.*,s.name subject_name FROM teacher_lesson_logs l JOIN subjects s ON s.id=l.subject_id ";
    public List<Lesson> lessons(Long user,Long classId) {
        scope(user,classId,false);
        return jdbc.query(LESSON_SELECT+"WHERE l.user_id=? AND l.class_id=? ORDER BY l.lesson_on DESC,l.id DESC",lessonMapper,user,classId);
    }
    @Transactional public void lesson(Long user,Long classId,Long id,LessonRequest request) {
        var cls=scope(user,classId,true);
        if(request.lessonOn().isAfter(LocalDate.now()) || request.lessonOn().isBefore(cls.getAcademicYear().getStartDate()) || request.lessonOn().isAfter(cls.getAcademicYear().getEndDate()))
            throw new IllegalArgumentException("Choisissez une séance réalisée pendant l’année scolaire.");
        Integer matches=jdbc.queryForObject("SELECT count(*) FROM class_subject_teacher a JOIN teachers t ON t.id=a.teacher_id WHERE a.class_id=? AND a.subject_id=? AND a.active=true AND t.user_id=? AND t.school_id=?",Integer.class,classId,request.subjectId(),user,cls.getSchool().getId());
        if(matches==null || matches==0)throw new AccessDeniedException("Cette matière ne vous est pas affectée.");
        if(id==null)jdbc.update("INSERT INTO teacher_lesson_logs(user_id,class_id,subject_id,lesson_on,title,content,objectives,next_work,shared) VALUES(?,?,?,?,?,?,?,?,?)",user,classId,request.subjectId(),request.lessonOn(),request.title().trim(),request.content().trim(),request.objectives().trim(),request.nextWork().trim(),request.shared());
        else requireChanged(jdbc.update("UPDATE teacher_lesson_logs SET subject_id=?,lesson_on=?,title=?,content=?,objectives=?,next_work=?,shared=? WHERE id=? AND user_id=? AND class_id=?",request.subjectId(),request.lessonOn(),request.title().trim(),request.content().trim(),request.objectives().trim(),request.nextWork().trim(),request.shared(),id,user,classId));
    }
    @Transactional public void removeLesson(Long user,Long classId,Long id) {
        scope(user,classId,true);requireChanged(jdbc.update("DELETE FROM teacher_lesson_logs WHERE id=? AND user_id=? AND class_id=?",id,user,classId));
    }
    private final org.springframework.jdbc.core.RowMapper<Note> noteMapper=(r,n)->new Note(r.getLong("id"),r.getString("title"),r.getString("content"),r.getObject("remind_on",LocalDate.class),r.getBoolean("done"));
    public List<Note> notes(Long user) {
        requireTeacher(user);return jdbc.query("SELECT * FROM teacher_private_notes WHERE user_id=? ORDER BY done,remind_on NULLS LAST,id DESC",noteMapper,user);
    }
    @Transactional public void note(Long user,Long id,NoteRequest r) {
        requireTeacher(user);
        if(id==null)jdbc.update("INSERT INTO teacher_private_notes(user_id,title,content,remind_on,done) VALUES(?,?,?,?,?)",user,r.title().trim(),r.content().trim(),r.remindOn(),r.done());
        else requireChanged(jdbc.update("UPDATE teacher_private_notes SET title=?,content=?,remind_on=?,done=? WHERE id=? AND user_id=?",r.title().trim(),r.content().trim(),r.remindOn(),r.done(),id,user));
    }
    @Transactional public void removeNote(Long user,Long id) {
        requireTeacher(user);requireChanged(jdbc.update("DELETE FROM teacher_private_notes WHERE id=? AND user_id=?",id,user));
    }
    public List<Support> support(Long user,Long classId) {
        var cls=scope(user,classId,false);
        return jdbc.query("""
          WITH roster AS(SELECT DISTINCT s.id,concat(s.first_name,' ',s.last_name) name FROM student_enrollments e JOIN students s ON s.id=e.student_id JOIN users u ON u.id=s.user_id WHERE e.class_id=? AND e.academic_year_id=? AND e.status='ACTIVE' AND s.school_id=?),
          attendance AS(SELECT DISTINCT ON(student_id,attendance_date) student_id,status FROM attendances WHERE class_id=? AND attendance_date BETWEEN ? AND ? ORDER BY student_id,attendance_date,id DESC),
          absent AS(SELECT student_id,count(*) n FROM attendance WHERE status='ABSENT' GROUP BY student_id),
          late_work AS(SELECT e.student_id,count(DISTINCT p.id) n FROM parent_portal_posts p JOIN student_enrollments e ON e.class_id=p.class_id AND e.academic_year_id=? AND e.status='ACTIVE' LEFT JOIN homework_progress h ON h.post_id=p.id AND h.student_id=e.student_id WHERE p.class_id=? AND p.school_id=? AND p.author_id=? AND p.kind='HOMEWORK' AND p.due_date<? AND (p.student_id IS NULL OR p.student_id=e.student_id) AND coalesce(h.status,'TO_DO')='TO_DO' GROUP BY e.student_id),
          latest_marks AS(SELECT DISTINCT ON(g.student_id,g.evaluation_id) g.student_id,g.value,g.max_value FROM grades g JOIN evaluations v ON v.id=g.evaluation_id AND v.class_subject_teacher_id=g.class_subject_teacher_id JOIN class_subject_teacher a ON a.id=v.class_subject_teacher_id JOIN teachers t ON t.id=a.teacher_id WHERE a.class_id=? AND t.user_id=? AND t.school_id=? ORDER BY g.student_id,g.evaluation_id,g.id DESC),
          marks AS(SELECT student_id,round(avg(value*20/max_value),2) average FROM latest_marks WHERE max_value>0 AND value BETWEEN 0 AND max_value GROUP BY student_id)
          SELECT r.*,coalesce(a.n,0) absences,coalesce(w.n,0) overdue,m.average,p.id plan_id,coalesce(p.action,'') action,p.review_on,coalesce(p.resolved,false) resolved,coalesce(p.family_message,'') family_message,coalesce(p.shared,false) shared
          FROM roster r LEFT JOIN absent a ON a.student_id=r.id LEFT JOIN late_work w ON w.student_id=r.id LEFT JOIN marks m ON m.student_id=r.id LEFT JOIN teacher_student_support p ON p.student_id=r.id AND p.class_id=? AND p.user_id=?
          WHERE coalesce(a.n,0)>=3 OR coalesce(w.n,0)>0 OR m.average<10 OR p.id IS NOT NULL ORDER BY r.name,r.id
          """,(r,n)->new Support(r.getLong("id"),r.getString("name"),r.getLong("absences"),r.getLong("overdue"),r.getBigDecimal("average"),r.getObject("plan_id",Long.class),r.getString("action"),r.getObject("review_on",LocalDate.class),r.getBoolean("resolved"),r.getString("family_message"),r.getBoolean("shared")),classId,cls.getAcademicYear().getId(),cls.getSchool().getId(),classId,LocalDate.now().minusDays(29).isAfter(cls.getAcademicYear().getStartDate())?LocalDate.now().minusDays(29):cls.getAcademicYear().getStartDate(),LocalDate.now(),cls.getAcademicYear().getId(),classId,cls.getSchool().getId(),user,LocalDate.now(),classId,user,cls.getSchool().getId(),classId,user);
    }
    @Transactional public void support(Long user,Long classId,Long studentId,SupportRequest r) {
        var cls=scope(user,classId,true);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM student_enrollments e JOIN students s ON s.id=e.student_id WHERE e.class_id=? AND e.academic_year_id=? AND e.student_id=? AND e.status='ACTIVE' AND s.school_id=?)",Boolean.class,classId,cls.getAcademicYear().getId(),studentId,cls.getSchool().getId())))throw new AccessDeniedException("Cet élève n’est pas inscrit dans votre classe.");
        if(r.shared() && r.familyMessage().isBlank())throw new IllegalArgumentException("Renseignez le message à partager avec la famille.");
        jdbc.update("INSERT INTO teacher_student_support(user_id,class_id,student_id,action,review_on,resolved,family_message,shared) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(user_id,class_id,student_id) DO UPDATE SET action=excluded.action,review_on=excluded.review_on,resolved=excluded.resolved,family_message=excluded.family_message,shared=excluded.shared",user,classId,studentId,r.action().trim(),r.reviewOn(),r.resolved(),r.familyMessage().trim(),r.shared());
    }
    public FamilyLearning familyLearning(Long user,Long studentId) {
        family.requireReadableStudent(user,studentId);var student=family.student(user,studentId);
        if(student.classId()==null)return new FamilyLearning(List.of(),List.of());
        var lessons=jdbc.query(LESSON_SELECT+"JOIN classes c ON c.id=l.class_id WHERE l.class_id=? AND c.school_id=? AND l.shared=true ORDER BY l.lesson_on DESC,l.id DESC",lessonMapper,student.classId(),student.schoolId());
        var support=jdbc.query("SELECT concat(u.first_name,' ',u.last_name) teacher_name,p.family_message,p.review_on,p.resolved FROM teacher_student_support p JOIN users u ON u.id=p.user_id JOIN classes c ON c.id=p.class_id WHERE p.student_id=? AND p.class_id=? AND c.school_id=? AND p.shared=true ORDER BY p.id DESC",(r,n)->new FamilySupport(r.getString("teacher_name"),r.getString("family_message"),r.getObject("review_on",LocalDate.class),r.getBoolean("resolved")),studentId,student.classId(),student.schoolId());
        return new FamilyLearning(lessons,support);
    }
    public List<Availability> availability(Long user) {
        requireTeacher(user);
        return jdbc.query("SELECT r.*,s.name school_name FROM teacher_availability_requests r JOIN teachers t ON t.id=r.teacher_id JOIN schools s ON s.id=t.school_id WHERE t.user_id=? ORDER BY r.starts_at DESC,r.id DESC",(r,n)->new Availability(r.getLong("id"),r.getString("school_name"),r.getObject("starts_at",LocalDateTime.class),r.getObject("ends_at",LocalDateTime.class),r.getString("reason"),r.getBoolean("replacement_requested"),r.getLong("conversation_id")),user);
    }
    @Transactional public void availability(Long user,AvailabilityRequest r) {
        var profile=teachers.findById(r.teacherId()).filter(t->t.getUser().getId().equals(user)).orElseThrow(()->new AccessDeniedException("Cette fiche ne vous appartient pas."));
        if(!r.endsAt().isAfter(r.startsAt()))throw new IllegalArgumentException("La fin doit suivre le début.");
        var school=profile.getSchool();var owner=school.getOwner();
        if(school.getStatus()!=SchoolStatus.ACTIVE || owner==null || owner.getId().equals(user) || !Boolean.TRUE.equals(owner.getActive()) || !Boolean.TRUE.equals(owner.getApproved()))throw new IllegalArgumentException("Aucun responsable administratif disponible.");
        String text="Indisponibilité du "+r.startsAt()+" au "+r.endsAt()+"\nRemplacement demandé : "+(r.replacementRequested()?"Oui":"Non")+"\n\n"+r.reason().trim()+"\n\nCette demande ne modifie pas automatiquement l’emploi du temps ni la paie.";
        var conversation=messages.sendTeacherMessage(user,new FamilyContactDto.TeacherMessageRequest(school.getId(),null,"[Indisponibilité] Demande à l’administration",text,List.of(owner.getId())),List.of()).getFirst();
        jdbc.update("INSERT INTO teacher_availability_requests(teacher_id,starts_at,ends_at,reason,replacement_requested,conversation_id) VALUES(?,?,?,?,?,?)",r.teacherId(),r.startsAt(),r.endsAt(),r.reason().trim(),r.replacementRequested(),conversation.id());
    }
    public Today today(Long user) {
        requireTeacher(user);var date=LocalDate.now();
        var classes=teacher.classes(user).stream().filter(c->c.currentYear() && SelectedAcademicYear.schoolMatches(c.schoolId()) && (SelectedAcademicYear.id(c.schoolId())==null || SelectedAcademicYear.id(c.schoolId()).equals(c.academicYearId()))).map(SelfServiceDto.TeacherClass::classId).toList();
        var schedule=jdbc.query("""
            SELECT DISTINCT s.id,s.day_of_week,s.start_time,s.end_time,c.id class_id,c.name class_name,k.name school_name,
              coalesce((SELECT string_agg(DISTINCT sub.name,', ' ORDER BY sub.name) FROM class_subject_teacher a JOIN subjects sub ON sub.id=a.subject_id WHERE a.teacher_id=t.id AND a.class_id=c.id AND a.active=true),'') subjects
            FROM teacher_schedule_slots s JOIN teachers t ON t.id=s.teacher_id JOIN classes c ON c.id=s.class_id AND c.school_id=t.school_id JOIN schools k ON k.id=c.school_id JOIN academic_years y ON y.id=c.academic_year_id
            WHERE t.user_id=? AND k.status='ACTIVE' AND s.day_of_week=? AND s.effective_from<=? AND (s.effective_to IS NULL OR s.effective_to>=?) AND ? BETWEEN y.start_date AND y.end_date
            ORDER BY s.start_time,c.id,s.id
            """,(r,n)->new SelfServiceDto.ScheduleEntry(r.getLong("id"),r.getInt("day_of_week"),r.getObject("start_time",LocalTime.class),r.getObject("end_time",LocalTime.class),r.getLong("class_id"),r.getString("class_name"),r.getString("school_name"),r.getString("subjects"),""),user,date.getDayOfWeek().getValue(),date,date,date).stream().filter(s->classes.contains(s.classId())).toList();
        long homework=0,marks=0;
        for(Long classId:classes) {
            homework+=jdbc.queryForObject("SELECT count(DISTINCT p.id) FROM parent_portal_posts p JOIN classes c ON c.id=p.class_id JOIN student_enrollments e ON e.class_id=p.class_id AND e.academic_year_id=c.academic_year_id AND e.status='ACTIVE' LEFT JOIN homework_progress h ON h.post_id=p.id AND h.student_id=e.student_id WHERE p.class_id=? AND p.author_id=? AND p.kind='HOMEWORK' AND p.due_date<=? AND (p.student_id IS NULL OR p.student_id=e.student_id) AND coalesce(h.status,'TO_DO')<>'CORRECTED'",Long.class,classId,user,date);
            marks+=jdbc.queryForObject("SELECT count(DISTINCT v.id) FROM evaluations v JOIN class_subject_teacher a ON a.id=v.class_subject_teacher_id JOIN teachers t ON t.id=a.teacher_id WHERE a.class_id=? AND a.active=true AND t.user_id=? AND v.eval_date<=? AND EXISTS(SELECT 1 FROM student_enrollments e WHERE e.class_id=a.class_id AND e.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM grades g WHERE g.student_id=e.student_id AND g.evaluation_id=v.id AND g.value IS NOT NULL))",Long.class,classId,user,date);
        }
        var reminders=notes(user).stream().filter(n->!n.done() && n.remindOn()!=null && !n.remindOn().isAfter(date)).toList();
        return new Today(date,schedule,List.of(new Task("Devoirs arrivés à échéance à vérifier ou corriger",homework,"devoirs"),new Task("Évaluations avec des notes à saisir",marks,"notes")),reminders);
    }
    private void requireChanged(int count) {if(count==0)throw new AccessDeniedException("Élément introuvable ou non autorisé.");}
}
