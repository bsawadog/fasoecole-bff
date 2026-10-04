package org.afritechinnovations.security;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Contrôles d'accès centralisés pour les API génériques.
 * <ul>
 *   <li>propriétaire : le propriétaire de l'établissement (ou un SUPER_ADMIN) ;</li>
 *   <li>personnel : propriétaire, administrateur ou enseignant rattaché à l'établissement ;</li>
 *   <li>membre : tout utilisateur rattaché à l'établissement (y compris parents et élèves).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessGuard {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final Set<String> STAFF_ROLES = Set.of("SCHOOL_ADMIN", "TEACHER");

    private final SchoolRepository schoolRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final SchoolPermissions permissions;

    // ------------------------------------------------------------------ identité

    public UserPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        return principal;
    }

    public Long currentUserId() {
        return current().getId();
    }

    public boolean isSuperAdmin() {
        return current().getRoles().contains(SUPER_ADMIN);
    }

    public void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("Action réservée à l'administrateur de la plateforme");
        }
    }

    public void requireSelfOrSuperAdmin(Long userId) {
        if (!isSuperAdmin() && !Objects.equals(currentUserId(), userId)) {
            throw new AccessDeniedException("Vous ne pouvez accéder qu'à vos propres données");
        }
    }

    // ------------------------------------------------------------------ établissement

    public School requireOwnedSchool(Long schoolId) {
        School school = findSchool(schoolId);
        if (!isSuperAdmin() && !isOwner(school)) {
            throw new AccessDeniedException("Action réservée au propriétaire de l'établissement");
        }
        return school;
    }

    /** Additional lifecycle check used only by operational school modules. */
    public void requireApprovedSchool(Long schoolId) {
        SchoolApprovalPolicy.requireApproved(findSchool(schoolId), isSuperAdmin());
    }

    /** Propriétaire, SUPER_ADMIN ou membre actif du personnel ayant reçu l'un des modules indiqués. */
    public School requireSchoolModule(Long schoolId, StaffModule... modules) {
        School school = findSchool(schoolId);
        if (allowsSchoolModule(school, modules)) return school;
        throw new AccessDeniedException("Vous n'avez pas accès à ce module pour cet établissement");
    }

    /** Vérification sans exception pour les branches qui dépendent des droits d'accès. */
    public boolean allowsSchoolModule(Long schoolId, StaffModule... modules) {
        return allowsSchoolModule(findSchool(schoolId), modules);
    }

    private boolean allowsSchoolModule(School school, StaffModule... modules) {
        if (isSuperAdmin() || isOwner(school)) {
            return true;
        }
        for (StaffModule module : modules) {
            if (permissions.staffAllows(school.getId(), currentUserId(), module)) {
                return true;
            }
        }
        return false;
    }

    public boolean ownsSchool(Long schoolId) {
        return isSuperAdmin() || isOwner(findSchool(schoolId));
    }

    public void requireSchoolStaff(Long schoolId) {
        if (!isStaff(findSchool(schoolId))) {
            throw new AccessDeniedException("Accès réservé au personnel de l'établissement");
        }
    }

    public void requireSchoolMember(Long schoolId) {
        School school = findSchool(schoolId);
        if (isStaff(school)) {
            return;
        }
        Long userId = currentUserId();
        boolean member = schoolUserRepository.findByUserId(userId).stream()
                .anyMatch(link -> link.getSchool().getId().equals(schoolId))
                || studentRepository.findByUserId(userId)
                        .map(student -> student.getSchool().getId().equals(schoolId)).orElse(false)
                || parentRepository.findByUserId(userId)
                        .map(parent -> parentStudentRepository.findByParentId(parent.getId()).stream()
                                .anyMatch(link -> link.getStudent().getSchool().getId().equals(schoolId)))
                        .orElse(false);
        if (!member) {
            throw new AccessDeniedException("Vous n'êtes pas rattaché à cet établissement");
        }
    }

    /** Vrai si l'utilisateur courant est propriétaire d'un établissement auquel {@code userId} est rattaché. */
    public boolean managesUser(Long userId) {
        if (isSuperAdmin() || Objects.equals(currentUserId(), userId)) {
            return true;
        }
        Long me = currentUserId();
        return schoolUserRepository.findByUserId(userId).stream()
                .map(SchoolUser::getSchool)
                .anyMatch(school -> school.getOwner() != null && me.equals(school.getOwner().getId()));
    }

    public void requireUserManager(Long userId) {
        if (!managesUser(userId)) {
            throw new AccessDeniedException("Cet utilisateur n'appartient à aucun de vos établissements");
        }
    }

    /** Vrai si l'utilisateur courant et {@code otherUserId} sont rattachés à au moins un même établissement. */
    public boolean sharesSchoolWith(Long otherUserId) {
        if (isSuperAdmin()) {
            return true;
        }
        Set<Long> mine = schoolIdsOf(currentUserId());
        return schoolIdsOf(otherUserId).stream().anyMatch(mine::contains);
    }

    private Set<Long> schoolIdsOf(Long userId) {
        Set<Long> ids = new HashSet<>();
        schoolRepository.findByOwnerId(userId).forEach(school -> ids.add(school.getId()));
        schoolUserRepository.findByUserId(userId).forEach(link -> ids.add(link.getSchool().getId()));
        teacherRepository.findByUserId(userId).forEach(teacher -> ids.add(teacher.getSchool().getId()));
        studentRepository.findByUserId(userId).ifPresent(student -> ids.add(student.getSchool().getId()));
        parentRepository.findByUserId(userId).ifPresent(parent -> parentStudentRepository.findByParentId(parent.getId())
                .forEach(link -> ids.add(link.getStudent().getSchool().getId())));
        return ids;
    }

    // ------------------------------------------------------------------ élèves

    /** Personnel de l'établissement de l'élève, l'élève lui-même ou l'un de ses parents. */
    public void requireStudentReader(Long studentId) {
        Student student = findStudent(studentId);
        if (isStaff(student.getSchool())) {
            return;
        }
        Long userId = currentUserId();
        if (student.getUser() != null && userId.equals(student.getUser().getId())) {
            return;
        }
        boolean parent = parentRepository.findByUserId(userId)
                .map(p -> parentStudentRepository.findByParentId(p.getId()).stream()
                        .anyMatch(link -> link.getStudent().getId().equals(studentId)))
                .orElse(false);
        if (!parent) {
            throw new AccessDeniedException("Vous n'avez pas accès au dossier de cet élève");
        }
    }

    // ------------------------------------------------------------------ résolution d'établissement

    public Long schoolOfClass(Long classId) {
        SchoolClass schoolClass = schoolClassRepository.findById(required(classId, "classe"))
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable : " + classId));
        return schoolClass.getSchool().getId();
    }

    public Long schoolOfYear(Long yearId) {
        return academicYearRepository.findById(required(yearId, "année scolaire"))
                .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable : " + yearId))
                .getSchool().getId();
    }

    public Long schoolOfStudent(Long studentId) {
        return findStudent(studentId).getSchool().getId();
    }

    public Long schoolOfTeacher(Long teacherId) {
        return findTeacher(teacherId).getSchool().getId();
    }

    public Long teacherUserId(Long teacherId) {
        Teacher teacher = findTeacher(teacherId);
        return teacher.getUser() != null ? teacher.getUser().getId() : null;
    }

    /** Vérifie que l'identifiant appartient bien à l'établissement attendu. */
    public void requireSame(Long expectedSchoolId, Long actualSchoolId, String what) {
        if (!Objects.equals(expectedSchoolId, actualSchoolId)) {
            throw new IllegalArgumentException("Le " + what + " n'appartient pas à cet établissement");
        }
    }

    // ------------------------------------------------------------------ interne

    private boolean isOwner(School school) {
        return school.getOwner() != null && currentUserId().equals(school.getOwner().getId());
    }

    private boolean isStaff(School school) {
        if (isSuperAdmin() || isOwner(school)) {
            return true;
        }
        Long userId = currentUserId();
        boolean linked = schoolUserRepository.findByUserId(userId).stream()
                .anyMatch(link -> link.getSchool().getId().equals(school.getId())
                        && link.getRole() != null && STAFF_ROLES.contains(link.getRole().getName()));
        return linked || permissions.isActiveStaff(school.getId(), userId)
                || teacherRepository.findByUserId(userId).stream()
                .anyMatch(teacher -> teacher.getSchool().getId().equals(school.getId()));
    }

    private School findSchool(Long schoolId) {
        School school = schoolRepository.findById(required(schoolId, "établissement"))
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable : " + schoolId));
        if (!isSuperAdmin() && (school.getStatus() == org.afritechinnovations.model.common.SchoolStatus.SUSPENDED
                || school.getStatus() == org.afritechinnovations.model.common.SchoolStatus.ARCHIVED)) {
            throw new AccessDeniedException("Cet établissement est désactivé. Contactez l’administrateur de la plateforme.");
        }
        if(!isSuperAdmin() && !isOwner(school) && (school.getStatus() == org.afritechinnovations.model.common.SchoolStatus.DRAFT
                || school.getStatus() == org.afritechinnovations.model.common.SchoolStatus.PENDING_APPROVAL))
            throw new AccessDeniedException("Cet établissement n’est pas encore activé");
        return school;
    }

    private Student findStudent(Long studentId) {
        return studentRepository.findById(required(studentId, "élève"))
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable : " + studentId));
    }

    private Teacher findTeacher(Long teacherId) {
        return teacherRepository.findById(required(teacherId, "enseignant"))
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable : " + teacherId));
    }

    private static Long required(Long id, String what) {
        if (id == null) {
            throw new IllegalArgumentException("Identifiant de " + what + " manquant");
        }
        return id;
    }
}
