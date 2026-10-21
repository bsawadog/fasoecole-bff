package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ClassRosterRowDto;
import org.afritechinnovations.dto.people.CreateRosterStudentRequest;
import org.afritechinnovations.dto.people.CreateStudentInvoiceRequest;
import org.afritechinnovations.dto.people.CreateStudentPaymentRequest;
import org.afritechinnovations.dto.people.NewStudentEnrollmentRequest;
import org.afritechinnovations.dto.people.OwnerEnrollmentDto;
import org.afritechinnovations.dto.people.StudentDetailDto;
import org.afritechinnovations.dto.people.UpdateParentProfileRequest;
import org.afritechinnovations.dto.people.CreateRosterParentRequest;
import org.afritechinnovations.dto.people.UpdateStudentProfileRequest;
import org.afritechinnovations.dto.people.UpsertAttendanceRequest;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Grade;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.afritechinnovations.model.finance.Payment;
import org.afritechinnovations.model.finance.PaymentMethod;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.GradeRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.communication.AbsenceReportRepository;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.afritechinnovations.service.communication.AbsenceReportJustification;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.finance.FeeTypeRepository;
import org.afritechinnovations.repository.finance.InvoiceRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ClassRosterService {

    private final SchoolPermissions permissions;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final FeeTypeRepository feeTypeRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AbsenceReportRepository absenceReportRepository;
    private final org.afritechinnovations.service.auth.EmailVerificationService invitations;

    public record ClassTeacher(Long teacherId, String teacherName, String subjectName) {}

    public List<ClassTeacher> getClassTeachers(Long classId, Long userId, boolean systemAdmin) {
        var schoolClass = requireOwnedClass(classId, userId, systemAdmin);
        return classSubjectTeacherRepository.findAllWithTeacherByClassId(classId).stream()
                .filter(ClassSubjectTeacher::isActive)
                .filter(a -> a.getTeacher().getSchool().getId().equals(schoolClass.getSchool().getId()))
                .map(a -> new ClassTeacher(a.getTeacher().getId(),
                        a.getTeacher().getUser().getFirstName() + " " + a.getTeacher().getUser().getLastName(),
                        a.getSubject().getName()))
                .distinct().toList();
    }

    public List<ClassRosterRowDto> getRoster(Long classId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        List<String> teacherNames = teacherNamesForClass(classId);

        return studentEnrollmentRepository
                .findStudentsWithUserByClassIdAndStatusIn(classId,
                        List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))
                .stream()
                .map(StudentEnrollment::getStudent)
                .filter(student -> student.getSchool().getId().equals(schoolClass.getSchool().getId()))
                .map(student -> toRow(student, teacherNames, true))
                .toList();
    }

    public ClassRosterRowDto updateStudentProfile(Long classId, Long studentId, UpdateStudentProfileRequest request,
                                                   Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        if (!student.getSchool().getId().equals(schoolClass.getSchool().getId()))
            throw new AccessDeniedException("Cet élève n’appartient pas à cet établissement");
        if (!systemAdmin && !student.getSchool().getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(student.getSchool().getId(), ownerId, StaffModule.STUDENTS)) {
            throw new AccessDeniedException("Vous ne pouvez modifier que les élèves de votre établissement");
        }

        if (!java.util.Objects.equals(importEmail(request.getEmail()), importEmail(student.getEmail()))) {
            if (!systemAdmin && !student.getSchool().getOwner().getId().equals(ownerId))
                throw new AccessDeniedException("Seul le propriétaire peut corriger le courriel avant activation.");
            User account = userRepository.findByIdForUpdate(student.getUser().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Compte introuvable"));
            if (!Boolean.FALSE.equals(account.getPasswordSet()) || !Boolean.FALSE.equals(account.getEmailVerified()))
                throw new IllegalArgumentException("Ce compte est déjà activé ou pris en main. Son titulaire doit modifier son courriel depuis Mon profil et confirmer la nouvelle adresse.");
            requireUnactivatedStudentEmailAccess(account, ownerId, systemAdmin);
            invitations.correctUnactivatedEmail(account.getId(), request.getEmail());
        }
        student.setFirstName(request.getFirstName().trim());
        student.setLastName(request.getLastName().trim());
        student.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        student.setRegistrationNumber(request.getRegistrationNumber().trim());
        student.setBirthDate(request.getBirthDate());
        student.setGender(request.getGender());
        studentRepository.save(student);

        return toRow(student, teacherNamesForClass(classId));
    }

    public record StudentAccessResult(ClassRosterRowDto student, boolean emailSent) { }

    public StudentAccessResult inviteStudentAccount(Long classId, Long studentId, String email,
                                                    Long actor, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, actor, systemAdmin);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable"));
        if (!student.getSchool().getId().equals(schoolClass.getSchool().getId())) {
            throw new AccessDeniedException("Cet élève n’appartient pas à cet établissement");
        }
        String loginEmail = importEmail(email);
        if (loginEmail == null) throw new IllegalArgumentException("Renseignez le courriel de connexion de l’élève.");
        User user = userRepository.findByIdForUpdate(student.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable"));
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Cet élève dispose déjà d’un compte de connexion. Utilisez son adresse de connexion actuelle pour envoyer un lien.");
        }
        if (!Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getPasswordSet())
                || Boolean.TRUE.equals(user.getEmailVerified())
                || sharedIdentityError(user, "STUDENT", student.getSchool().getId()) != null
                || studentRepository.findAllByUserId(user.getId()).stream().anyMatch(s -> !studentId.equals(s.getId()))) {
            throw new IllegalArgumentException("Ce compte ne peut pas être activé depuis cette fiche. Contactez le support technique pour vérifier ses accès.");
        }
        if (userRepository.findByEmailIgnoreCase(loginEmail).isPresent()) {
            throw new IllegalArgumentException("Ce courriel identifie déjà un compte. Aucun rattachement automatique n’a été effectué. Utilisez la demande d’accès avec le matricule, à faire approuver par l’établissement, ou une adresse de connexion propre à l’élève.");
        }
        user.setEmail(loginEmail);
        user.setEmailVerified(false);
        user.revokeSessions();
        userRepository.saveAndFlush(user);
        boolean sent = invitations.sendInvitation(user);
        return new StudentAccessResult(toRow(student, teacherNamesForClass(classId)), sent);
    }

    public ClassRosterRowDto addParent(Long classId, Long studentId, CreateRosterParentRequest request,
                                       Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        boolean inClass = studentEnrollmentRepository.findByStudentId(studentId).stream()
                .anyMatch(enrollment -> enrollment.getSchoolClass().getId().equals(classId)
                        && (enrollment.getStatus() == EnrollmentStatus.ACTIVE
                        || enrollment.getStatus() == EnrollmentStatus.COMPLETED));
        if (!inClass || !student.getSchool().getId().equals(schoolClass.getSchool().getId())) {
            throw new AccessDeniedException("Cet élève n'appartient pas à cette classe");
        }
        attachGuardians(student, List.of(new OwnerEnrollmentDto.Guardian(null, request.getFirstName(),
                request.getLastName(), request.getEmail(), request.getPhone(), request.getRelationship())));
        return toRow(student, teacherNamesForClass(classId));
    }

    public ClassRosterRowDto updateParentProfile(Long classId, Long parentId, UpdateParentProfileRequest request,
                                                  Long ownerId, boolean systemAdmin) {
        requireOwnedClass(classId, ownerId, systemAdmin);
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent introuvable: " + parentId));

        Student studentInClass = parentStudentRepository.findByParentId(parentId).stream()
                .map(ParentStudent::getStudent)
                .filter(student -> request.getStudentId() == null || request.getStudentId().equals(student.getId()))
                .filter(student -> studentEnrollmentRepository.findByStudentId(student.getId()).stream()
                        .anyMatch(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE
                                && enrollment.getSchoolClass().getId().equals(classId)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ce parent n'est pas rattaché à un élève de cette classe"));

        if (!systemAdmin && !studentInClass.getSchool().getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(studentInClass.getSchool().getId(), ownerId, StaffModule.STUDENTS)) {
            throw new AccessDeniedException("Vous ne pouvez modifier que les parents de votre établissement");
        }

        User user = userRepository.findById(parent.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable"));
        String parentEmail = request.getEmail() == null || request.getEmail().isBlank()
                ? null : request.getEmail().trim().toLowerCase();
        if (parentEmail != null && !parentEmail.equalsIgnoreCase(user.getEmail())) {
            User existing = userRepository.findByEmailIgnoreCase(parentEmail).orElse(null);
            if (existing != null) {
                if (request.getStudentId() == null) {
                    throw new IllegalArgumentException("Précisez l’élève pour rattacher ce compte existant comme parent.");
                }
                if (!Boolean.TRUE.equals(existing.getActive())) throw new IllegalArgumentException("Ce compte est inactif.");
                if (existing.getId().equals(studentInClass.getUser().getId())) {
                    throw new IllegalArgumentException("Un élève ne peut pas être son propre parent");
                }
                Parent replacement = parentRepository.findByUserId(existing.getId())
                        .orElseGet(() -> parentRepository.save(Parent.builder().user(existing).build()));
                ParentStudent previous = parentStudentRepository.findByParentId(parentId).stream()
                        .filter(link -> studentInClass.getId().equals(link.getStudent().getId())).findFirst().orElseThrow();
                Role parentRole = roleRepository.findByName("PARENT")
                        .orElseThrow(() -> new IllegalStateException("Rôle non configuré: PARENT"));
                linkGuardian(studentInClass, replacement, previous.getRelationship(), parentRole, new HashSet<>());
                parentStudentRepository.delete(previous);
                return toRow(studentInClass, teacherNamesForClass(classId));
            }
        }
        String sharedIdentityError = sharedIdentityError(user, "PARENT", studentInClass.getSchool().getId());
        boolean sharedAccount = sharedIdentityError != null;
        if (sharedAccount) {
            if (identityChanged(user, request.getFirstName(), request.getLastName(), parentEmail, request.getPhone())) {
                throw new IllegalArgumentException(sharedIdentityError);
            }
            return toRow(studentInClass, teacherNamesForClass(classId));
        }
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        boolean emailChanged = !java.util.Objects.equals(parentEmail, user.getEmail() == null ? null : user.getEmail().toLowerCase());
        if (emailChanged) {
            user.setEmailVerified(false);
            user.revokeSessions();
        }
        user.setEmail(parentEmail);
        user.setPhone(request.getPhone());
        userRepository.save(user);

        if (emailChanged && parentEmail != null) invitations.sendInvitation(user);

        return toRow(studentInClass, teacherNamesForClass(classId));
    }

    public void removeStudentFromClass(Long classId, Long studentId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        boolean enrolledInClass = studentEnrollmentRepository.findByStudentId(studentId).stream()
                .anyMatch(enrollment -> enrollment.getSchoolClass().getId().equals(classId));
        if (!enrolledInClass) {
            throw new IllegalArgumentException("Cet élève n'est pas inscrit dans cette classe");
        }
        if (!systemAdmin && !student.getSchool().getId().equals(schoolClass.getSchool().getId())) {
            throw new AccessDeniedException("Vous ne pouvez supprimer que les élèves de votre établissement");
        }
        // Retire le dossier scolaire, jamais le compte partagé avec d'autres rôles ou écoles.
        Long userId = student.getUser().getId();
        boolean otherDossier = studentRepository.findAllByUserId(userId).stream()
                .anyMatch(other -> !studentId.equals(other.getId()) && student.getSchool().getId().equals(other.getSchool().getId()));
        if (!otherDossier) {
            schoolUserRepository.findByUserId(userId).stream()
                    .filter(link -> student.getSchool().getId().equals(link.getSchool().getId()) && "STUDENT".equals(link.getRole().getName()))
                    .forEach(schoolUserRepository::delete);
        }
        studentRepository.delete(student);
    }

    /**
     * Transfère un élève vers une autre classe de la même année scolaire. L'inscription courante est
     * conservée en historique (TRANSFERRED) : le dossier de l'élève (notes, présences, factures, parents)
     * reste rattaché au même élève.
     */
    public ClassRosterRowDto transferStudent(Long classId, Long studentId, Long targetClassId,
                                             Long ownerId, boolean systemAdmin) {
        SchoolClass source = requireOwnedClass(classId, ownerId, systemAdmin);
        if (classId.equals(targetClassId)) {
            throw new IllegalArgumentException("L'élève est déjà inscrit dans cette classe");
        }
        SchoolClass target = requireOwnedClass(targetClassId, ownerId, systemAdmin);
        if (!target.getSchool().getId().equals(source.getSchool().getId())) {
            throw new IllegalArgumentException("La classe de destination doit appartenir au même établissement");
        }

        StudentEnrollment current = studentEnrollmentRepository
                .findByStudentIdAndSchoolClassIdAndStatus(studentId, classId, EnrollmentStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Seul un élève actif de cette classe peut être transféré"));

        Long targetYearId = target.getAcademicYear().getId();
        if (!targetYearId.equals(source.getAcademicYear().getId())) {
            studentEnrollmentRepository.findByStudentIdAndAcademicYearId(studentId, targetYearId).stream()
                    .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                    .findFirst()
                    .ifPresent(e -> {
                        throw new IllegalArgumentException("L'élève est déjà inscrit en "
                                + e.getSchoolClass().getName() + " pour l'année "
                                + target.getAcademicYear().getLabel()
                                + ". Transférez-le depuis cette classe.");
                    });
        }

        if (target.getCapacity() != null) {
            long enrolled = studentEnrollmentRepository
                    .findBySchoolClassIdAndStatus(target.getId(), EnrollmentStatus.ACTIVE).size();
            if (enrolled >= target.getCapacity()) {
                throw new IllegalArgumentException(
                        "La classe " + target.getName() + " est complète (" + target.getCapacity() + " places)");
            }
        }

        current.setStatus(EnrollmentStatus.TRANSFERRED);
        current.setDecidedAt(LocalDateTime.now());
        studentEnrollmentRepository.save(current);

        studentEnrollmentRepository.save(StudentEnrollment.builder()
                .student(current.getStudent())
                .schoolClass(target)
                .academicYear(target.getAcademicYear())
                .status(EnrollmentStatus.ACTIVE)
                .enrollmentDate(LocalDate.now())
                .build());

        return toRow(current.getStudent(), teacherNamesForClass(target.getId()));
    }

    public ClassRosterRowDto createStudent(Long classId, CreateRosterStudentRequest request,
                                            Long ownerId, boolean systemAdmin) {
        return enrollNewStudent(requireOwnedClass(classId, ownerId, systemAdmin), request);
    }

    /** Cumulative paid amount: a retry adds only the missing payment, never the whole amount twice. */
    public ClassRosterRowDto importStudent(Long classId,
            org.afritechinnovations.dto.people.ImportRosterStudentRequest request, Long actor, boolean admin) {
        SchoolClass schoolClass = requireOwnedClass(classId, actor, admin);
        schoolClass.getAcademicYear().requireOpen();
        if (request.getRegistrationNumber() == null || request.getRegistrationNumber().isBlank())
            throw new IllegalArgumentException("Le matricule est obligatoire pour retrouver un élève importé.");
        boolean financial = request.getFees() != null || request.getPaid() != null;
        if (financial && !admin && !schoolClass.getSchool().getOwner().getId().equals(actor)
                && !permissions.staffAllows(schoolClass.getSchool().getId(), actor, StaffModule.FINANCE))
            throw new AccessDeniedException("L’accès aux frais et paiements est requis pour cet import.");
        // Lock the class for concurrent imports, then the student for concurrent imports across classes.
        schoolClassRepository.lockForRosterImport(classId);
        Student student = studentRepository.findBySchoolIdAndRegistrationNumberForUpdate(
                schoolClass.getSchool().getId(), request.getRegistrationNumber().trim()).orElse(null);
        if (student != null) {
            Student found = student;
            boolean enrolled = studentEnrollmentRepository.findByStudentIdAndAcademicYearId(student.getId(), schoolClass.getAcademicYear().getId())
                    .stream().anyMatch(e -> classId.equals(e.getSchoolClass().getId())
                            && (e.getStatus() == EnrollmentStatus.ACTIVE || e.getStatus() == EnrollmentStatus.COMPLETED));
            if (!enrolled || !found.getFirstName().equalsIgnoreCase(request.getFirstName().trim())
                    || !found.getLastName().equalsIgnoreCase(request.getLastName().trim())
                    || importEmail(request.getEmail()) != null && !java.util.Objects.equals(importEmail(found.getEmail()), importEmail(request.getEmail())))
                throw new IllegalArgumentException("Le matricule existe avec une autre identité ou dans une autre classe. Vérifiez le dossier.");
        } else student = createEnrolledStudent(schoolClass, request);
        if (financial) importStudentBilling(student, schoolClass, request, actor, admin);
        if (request.getParent() != null) importStudentParent(student, request.getParent());
        return toRow(student, teacherNamesForClass(classId), true);
    }

    private void importStudentParent(Student student, CreateRosterParentRequest request) {
        if (request.getFirstName() == null || request.getFirstName().isBlank()
                || request.getLastName() == null || request.getLastName().isBlank())
            throw new IllegalArgumentException("Parent : prénom et nom obligatoires.");
        String email = importEmail(request.getEmail());
        var existing = parentStudentRepository.findByStudentIdWithParentUser(student.getId()).stream()
                .filter(link -> {
                    User user = link.getParent().getUser();
                    return email != null ? email.equals(importEmail(user.getEmail()))
                            : user.getFirstName().equalsIgnoreCase(request.getFirstName().trim())
                            && user.getLastName().equalsIgnoreCase(request.getLastName().trim());
                }).toList();
        if (existing.size() > 1) throw new IllegalArgumentException("Plusieurs parents correspondent à ce nom. Précisez le courriel ou vérifiez la fiche élève.");
        if (existing.size() == 1) {
            // Reimporting the same student must not create another parent without an email.
            var link = existing.getFirst();
            if (request.getRelationship() != null && !request.getRelationship().isBlank()) {
                link.setRelationship(request.getRelationship().trim());
                parentStudentRepository.save(link);
            }
            return;
        }
        attachGuardians(student, List.of(new OwnerEnrollmentDto.Guardian(null, request.getFirstName(),
                request.getLastName(), email, request.getPhone(), request.getRelationship())));
    }

    private void importStudentBilling(Student student, SchoolClass schoolClass,
            org.afritechinnovations.dto.people.ImportRosterStudentRequest request, Long actor, boolean admin) {
        if (request.getFees() == null || request.getFeeTypeName() == null || request.getDueDate() == null)
            throw new IllegalArgumentException("Renseignez frais, type_frais et échéance ensemble.");
        BigDecimal fees = importMoney(request.getFees());
        BigDecimal targetPaid = request.getPaid() == null ? null : importMoney(request.getPaid());
        if (targetPaid != null && targetPaid.compareTo(fees) > 0)
            throw new IllegalArgumentException("Le paiement cumulé dépasse les frais.");
        var types = feeTypeRepository.findBySchoolId(schoolClass.getSchool().getId()).stream()
                .filter(f -> f.getName().equalsIgnoreCase(request.getFeeTypeName().trim()) && f.isActive())
                .filter(f -> f.getLevel() == null || schoolClass.getLevel() != null && f.getLevel().getId().equals(schoolClass.getLevel().getId())).toList();
        if (types.size() != 1) throw new IllegalArgumentException("Type de frais introuvable, inadapté au niveau ou ambigu. Configurez-le dans Frais et paiements.");
        invoiceRepository.lockStudentInvoices(student.getId());
        var matching = invoiceRepository.findByStudentId(student.getId()).stream()
                .filter(i -> i.getAcademicYear().getId().equals(schoolClass.getAcademicYear().getId())
                        && i.getFeeType().getId().equals(types.getFirst().getId()) && i.getDueDate().equals(request.getDueDate())).toList();
        if (matching.size() > 1) throw new IllegalArgumentException("Plusieurs frais correspondent à cette échéance. Corrigez-les dans la fiche élève.");
        Invoice invoice = matching.isEmpty() ? Invoice.builder().student(student).feeType(types.getFirst())
                .academicYear(schoolClass.getAcademicYear()).dueDate(request.getDueDate()).amountDue(fees).build() : matching.getFirst();
        if (invoice.getStatus() == InvoiceStatus.CANCELLED)
            throw new IllegalArgumentException("Ce frais est annulé. Vérifiez la fiche élève.");
        if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().signum() > 0)
            throw new IllegalArgumentException("Ce frais bénéficie d’une réduction : utilisez la fiche élève pour le modifier.");
        var payments = invoice.getId() == null ? List.<Payment>of() : paymentRepository.findByInvoiceId(invoice.getId());
        BigDecimal alreadyPaid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fees.compareTo(alreadyPaid) < 0 || targetPaid != null && targetPaid.compareTo(alreadyPaid) < 0)
            throw new IllegalArgumentException("L’import ne peut pas diminuer les paiements déjà enregistrés. Corrigez les versements dans la fiche élève.");
        invoice.setAmountDue(fees);
        invoice = invoiceRepository.save(invoice);
        if (targetPaid != null && targetPaid.compareTo(alreadyPaid) > 0) {
            if (request.getPaymentDate() == null || request.getPaymentMethod() == null || request.getPaymentDate().isAfter(LocalDate.now()))
                throw new IllegalArgumentException("Renseignez une date de paiement non future et un mode de paiement.");
            CreateStudentPaymentRequest payment = new CreateStudentPaymentRequest();
            payment.setAmount(targetPaid.subtract(alreadyPaid));
            payment.setPaymentDate(request.getPaymentDate()); payment.setMethod(request.getPaymentMethod());
            addPayment(student.getId(), invoice.getId(), payment, actor, admin);
        } else updateInvoicePaymentStatus(invoice, payments);
    }

    private static BigDecimal importMoney(BigDecimal value) {
        if (value.signum() < 0 || value.precision() - value.scale() > 10)
            throw new IllegalArgumentException("Montant invalide.");
        try { return value.setScale(2, java.math.RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Deux décimales maximum."); }
    }

    private static String importEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Inscrit le compte public existant, sans créer un second compte ni modifier son mot de passe. */
    public void attachApprovedStudent(User user, org.afritechinnovations.model.common.School school,
                                      Long classId, String requestedNumber) {
        if (classId == null) throw new IllegalArgumentException("Choisissez une classe pour inscrire cet élève");
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));
        if (!school.getId().equals(schoolClass.getSchool().getId())) {
            throw new IllegalArgumentException("La classe doit appartenir à l'établissement sélectionné");
        }
        if (schoolClass.getAcademicYear() == null || !school.getId().equals(schoolClass.getAcademicYear().getSchool().getId())) {
            throw new IllegalArgumentException("L'année scolaire de cette classe est invalide");
        }
        Student student = studentRepository.findAllByUserId(user.getId()).stream()
                .filter(s -> school.getId().equals(s.getSchool().getId())).findFirst().orElse(null);
        if (student != null) {
            var current = studentEnrollmentRepository.findByStudentIdAndAcademicYearId(student.getId(), schoolClass.getAcademicYear().getId())
                    .stream().filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE).findFirst();
            if (current.isPresent()) {
                if (classId.equals(current.get().getSchoolClass().getId())) return;
                throw new IllegalArgumentException("Cet élève est déjà inscrit dans une autre classe : utilisez le transfert");
            }
        }
        if (schoolClass.getCapacity() != null && studentEnrollmentRepository
                .findBySchoolClassIdAndStatus(classId, EnrollmentStatus.ACTIVE).size() >= schoolClass.getCapacity()) {
            throw new IllegalArgumentException("Cette classe est complète");
        }
        if (student == null) {
            String number = requestedNumber == null || requestedNumber.isBlank()
                    ? nextRegistrationNumber(schoolClass) : requestedNumber.trim();
            if (studentRepository.findBySchoolIdAndRegistrationNumber(school.getId(), number).isPresent()) {
                throw new IllegalArgumentException("Ce matricule est déjà utilisé dans cet établissement");
            }
            student = studentRepository.save(Student.builder().user(user).school(school).registrationNumber(number).build());
        }
        studentEnrollmentRepository.save(StudentEnrollment.builder().student(student).schoolClass(schoolClass)
                .academicYear(schoolClass.getAcademicYear()).status(EnrollmentStatus.ACTIVE).enrollmentDate(LocalDate.now()).build());
    }

    /** Crée le compte, la fiche élève et l'inscription ACTIVE dans la classe (droits vérifiés par l'appelant). */
    public ClassRosterRowDto enrollNewStudent(SchoolClass schoolClass, CreateRosterStudentRequest request) {
        return toRow(createEnrolledStudent(schoolClass, request), teacherNamesForClass(schoolClass.getId()));
    }

    /**
     * Inscrit un nouvel élève, lui facture tous les frais applicables pour l'année de la classe et encaisse
     * immédiatement les montants versés (une référence de reçu par paiement). Droits vérifiés par l'appelant.
     */
    public OwnerEnrollmentDto.RegistrationResult enrollNewStudentWithFees(SchoolClass schoolClass,
                                                                         NewStudentEnrollmentRequest request) {
        List<OwnerEnrollmentDto.FeeLine> lines = new ArrayList<>(request.getFees() == null ? List.of() : request.getFees());
        Set<Long> submittedFees = lines.stream().map(OwnerEnrollmentDto.FeeLine::feeTypeId)
                .collect(java.util.stream.Collectors.toSet());
        feeTypeRepository.findBySchoolId(schoolClass.getSchool().getId()).stream()
                .filter(FeeType::isActive)
                .filter(fee -> fee.getLevel() == null || (schoolClass.getLevel() != null
                        && fee.getLevel().getId().equals(schoolClass.getLevel().getId())))
                .filter(fee -> !submittedFees.contains(fee.getId()))
                .forEach(fee -> lines.add(new OwnerEnrollmentDto.FeeLine(fee.getId(), BigDecimal.ZERO)));
        List<FeeType> feeTypes = validateEnrollmentFees(schoolClass, lines, request.getPaymentMethod());

        Student student = createEnrolledStudent(schoolClass, request);
        attachGuardians(student, request.getGuardians());
        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        LocalDate yearEnd = schoolClass.getAcademicYear().getEndDate();
        LocalDate dueDate = yearEnd != null && yearEnd.isAfter(LocalDate.now()) ? yearEnd : LocalDate.now();
        List<StudentDetailDto.InvoiceInfo> invoices = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            FeeType feeType = feeTypes.get(i);
            BigDecimal paid = lines.get(i).amountPaid() == null ? BigDecimal.ZERO : lines.get(i).amountPaid();
            Invoice invoice = invoiceRepository.save(Invoice.builder()
                    .student(student)
                    .feeType(feeType)
                    .academicYear(schoolClass.getAcademicYear())
                    .amountDue(feeType.getAmount())
                    .dueDate(dueDate)
                    .status(InvoiceStatus.PENDING)
                    .build());
            List<Payment> payments = new ArrayList<>();
            if (paid.signum() > 0) {
                payments.add(paymentRepository.save(Payment.builder()
                        .invoice(invoice)
                        .amount(paid)
                        .paymentDate(paymentDate)
                        .method(request.getPaymentMethod())
                        .reference(generatePaymentReference())
                        .build()));
            }
            updateInvoicePaymentStatus(invoice, payments);
            invoices.add(toInvoiceInfo(invoice, payments));
        }

        return new OwnerEnrollmentDto.RegistrationResult(
                toRow(student, teacherNamesForClass(schoolClass.getId())),
                schoolClass.getSchool().getName(),
                schoolClass.getName(),
                schoolClass.getAcademicYear().getLabel(),
                invoices);
    }

    private List<FeeType> validateEnrollmentFees(SchoolClass schoolClass, List<OwnerEnrollmentDto.FeeLine> lines,
                                                 PaymentMethod method) {
        Set<Long> seen = new HashSet<>();
        List<FeeType> feeTypes = new ArrayList<>();
        for (OwnerEnrollmentDto.FeeLine line : lines) {
            if (!seen.add(line.feeTypeId())) {
                throw new IllegalArgumentException("Un même frais ne peut être ajouté qu'une fois");
            }
            FeeType feeType = feeTypeRepository.findById(line.feeTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("Type de frais introuvable: " + line.feeTypeId()));
            if (!feeType.getSchool().getId().equals(schoolClass.getSchool().getId())) {
                throw new IllegalArgumentException("Ce type de frais n'appartient pas à l'établissement");
            }
            if (!feeType.isActive()) {
                throw new IllegalArgumentException("Le frais « " + feeType.getName() + " » est archivé");
            }
            if (feeType.getLevel() != null && schoolClass.getLevel() != null
                    && !feeType.getLevel().getId().equals(schoolClass.getLevel().getId())) {
                throw new IllegalArgumentException("Le frais « " + feeType.getName() + " » ne concerne pas le niveau de cette classe");
            }
            BigDecimal paid = line.amountPaid() == null ? BigDecimal.ZERO : line.amountPaid();
            if (paid.signum() < 0) {
                throw new IllegalArgumentException("Le montant versé ne peut pas être négatif");
            }
            if (paid.compareTo(feeType.getAmount()) > 0) {
                throw new IllegalArgumentException("Le montant versé pour « " + feeType.getName()
                        + " » dépasse le montant dû (" + feeType.getAmount() + ")");
            }
            if (paid.signum() > 0 && method == null) {
                throw new IllegalArgumentException("Choisissez le mode de paiement");
            }
            feeTypes.add(feeType);
        }
        return feeTypes;
    }

    private Student createEnrolledStudent(SchoolClass schoolClass, CreateRosterStudentRequest request) {
        if (schoolClass.getCapacity() != null && studentEnrollmentRepository
                .findBySchoolClassIdAndStatus(schoolClass.getId(), EnrollmentStatus.ACTIVE).size() >= schoolClass.getCapacity()) {
            throw new IllegalArgumentException("Cette classe est complète");
        }
        String email = request.getEmail() == null || request.getEmail().isBlank()
                ? null : request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        String registrationNumber = request.getRegistrationNumber() == null ? "" : request.getRegistrationNumber().trim();
        if (registrationNumber.isEmpty()) {
            registrationNumber = nextRegistrationNumber(schoolClass);
        } else if (studentRepository.findBySchoolIdAndRegistrationNumber(schoolClass.getSchool().getId(),
                registrationNumber).isPresent()) {
            throw new IllegalArgumentException("Ce matricule est déjà utilisé dans cet établissement");
        }

        // A contact address is not proof of account ownership (siblings may share a parent's address).
        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .passwordSet(false)
                .phone(request.getPhone())
                .active(true)
                .approved(true)
                .build();
        if (email != null && userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("Ce courriel personnel est déjà associé à un compte. Utilisez une demande d’accès avec le matricule ; aucun rattachement automatique n’est effectué. Le courriel d’un parent doit être renseigné dans la fiche parent.");
        }
        user.setApproved(true);
        user = userRepository.save(user);

        boolean member = schoolUserRepository.findByUserId(user.getId()).stream()
                .anyMatch(link -> schoolClass.getSchool().getId().equals(link.getSchool().getId()) && "STUDENT".equals(link.getRole().getName()));
        if (!member) {
            Role studentRole = roleRepository.findByName("STUDENT")
                    .orElseThrow(() -> new IllegalStateException("Rôle non configuré: STUDENT"));
            schoolUserRepository.save(SchoolUser.builder()
                    .user(user)
                    .school(schoolClass.getSchool())
                    .role(studentRole)
                    .build());
        }

        Student student = Student.builder()
                .user(user)
                .school(schoolClass.getSchool())
                .firstName(request.getFirstName().trim()).lastName(request.getLastName().trim())
                .email(email).phone(request.getPhone())
                .registrationNumber(registrationNumber)
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .build();
        student = studentRepository.save(student);

        studentEnrollmentRepository.save(StudentEnrollment.builder()
                .student(student)
                .schoolClass(schoolClass)
                .academicYear(schoolClass.getAcademicYear())
                .status(EnrollmentStatus.ACTIVE)
                .enrollmentDate(LocalDate.now())
                .build());

        return student;
    }

    /**
     * Matricule incrémental par établissement et par année d'entrée : MAT-2026-001, MAT-2026-002…
     * L'année est celle du début de l'année scolaire de la classe d'accueil.
     */
    synchronized String nextRegistrationNumber(SchoolClass schoolClass) {
        AcademicYear year = schoolClass.getAcademicYear();
        int startYear = year != null && year.getStartDate() != null ? year.getStartDate().getYear() : LocalDate.now().getYear();
        String prefix = "MAT-" + startYear + "-";
        Long schoolId = schoolClass.getSchool().getId();
        int max = studentRepository.findRegistrationNumbersBySchoolAndPrefix(schoolId, prefix).stream()
                .map(value -> value.substring(prefix.length()))
                .filter(suffix -> suffix.matches("\\d{1,9}"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        String candidate;
        do {
            candidate = prefix + String.format("%03d", ++max);
        } while (studentRepository.findBySchoolIdAndRegistrationNumber(schoolId, candidate).isPresent());
        return candidate;
    }

    /** Crée (ou retrouve par courriel) chaque parent/tuteur et le rattache à l'élève. */
    private void attachGuardians(Student student, List<OwnerEnrollmentDto.Guardian> guardians) {
        if (guardians == null || guardians.isEmpty()) {
            return;
        }
        Role parentRole = roleRepository.findByName("PARENT")
                .orElseThrow(() -> new IllegalStateException("Rôle non configuré: PARENT"));
        Set<Long> linked = new HashSet<>();
        for (OwnerEnrollmentDto.Guardian guardian : guardians) {
            if (guardian.parentId() != null) {
                Parent existing = parentRepository.findById(guardian.parentId())
                        .orElseThrow(() -> new IllegalArgumentException("Parent introuvable : " + guardian.parentId()));
                if (student.getUser() != null && existing.getUser().getId().equals(student.getUser().getId())) {
                    throw new IllegalArgumentException("Un élève ne peut pas être son propre parent");
                }
                linkGuardian(student, existing, guardian.relationship(), parentRole, linked);
                continue;
            }
            String email = guardian.email() == null || guardian.email().isBlank()
                    ? null : guardian.email().trim().toLowerCase();
            String phone = guardian.phone() == null || guardian.phone().isBlank() ? null : guardian.phone().trim();

            User user = email == null ? null : userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (user != null && !Boolean.TRUE.equals(user.getActive())) {
                throw new IllegalArgumentException("Ce compte est inactif. Contactez l’administration de la plateforme.");
            }
            if (user != null && student.getUser() != null && user.getId().equals(student.getUser().getId())) {
                throw new IllegalArgumentException("Un élève ne peut pas être son propre parent");
            }
            if (user == null) {
                user = userRepository.save(User.builder()
                        .firstName(guardian.firstName().trim())
                        .lastName(guardian.lastName().trim())
                        .email(email)
                        .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .passwordSet(false)
                        .phone(phone)
                        .active(true)
                        .approved(true)
                        .build());
            }
            if (!Boolean.TRUE.equals(user.getApproved())) {
                user.setApproved(true);
                userRepository.save(user);
            }

            User parentUser = user;
            Parent parent = parentRepository.findByUserId(parentUser.getId())
                    .orElseGet(() -> parentRepository.save(Parent.builder().user(parentUser).build()));
            linkGuardian(student, parent, guardian.relationship(), parentRole, linked);
        }
    }

    private void linkGuardian(Student student, Parent parent, String relationship, Role parentRole, Set<Long> linked) {
        if (!linked.add(parent.getId())) {
            return;
        }
        User parentUser = parent.getUser();
        if (!Boolean.TRUE.equals(parentUser.getActive())) throw new IllegalArgumentException("Ce compte est inactif.");
        if (!Boolean.TRUE.equals(parentUser.getApproved())) {
            parentUser.setApproved(true);
            userRepository.save(parentUser);
        }
        if (parentStudentRepository.findByStudentIdWithParentUser(student.getId()).stream()
                .anyMatch(link -> link.getParent().getId().equals(parent.getId()))) return;
        boolean inSchool = schoolUserRepository.findByUserId(parentUser.getId()).stream()
                .anyMatch(su -> su.getSchool().getId().equals(student.getSchool().getId())
                        && "PARENT".equals(su.getRole().getName()));
        if (!inSchool) {
            schoolUserRepository.save(SchoolUser.builder()
                    .user(parentUser).school(student.getSchool()).role(parentRole).build());
        }
        parentStudentRepository.save(ParentStudent.builder()
                .parent(parent)
                .student(student)
                .relationship(relationship == null || relationship.isBlank() ? null : relationship.trim())
                .build());
        invitations.sendAccessAssigned(parentUser, student.getSchool(), "Parent");
    }

    public StudentDetailDto getStudentDetail(Long studentId, Long ownerId, boolean systemAdmin) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        if (!systemAdmin && !student.getSchool().getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(student.getSchool().getId(), ownerId, StaffModule.STUDENTS)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que les élèves de votre établissement");
        }

        User user = student.getUser();

        StudentEnrollment activeEnrollment = studentEnrollmentRepository.findByStudentId(studentId).stream()
                .filter(enrollment -> (enrollment.getStatus() == EnrollmentStatus.ACTIVE || enrollment.getStatus() == EnrollmentStatus.COMPLETED) && org.afritechinnovations.service.academic.SelectedAcademicYear.matches(enrollment.getAcademicYear()))
                .findFirst()
                .orElse(null);
        String className = activeEnrollment != null ? activeEnrollment.getSchoolClass().getName() : null;

        List<ClassRosterRowDto.ParentInfo> parents = parentStudentRepository
                .findByStudentIdWithParentUser(studentId)
                .stream()
                .map(ps -> {
                    Parent parent = ps.getParent();
                    User parentUser = parent.getUser();
                    return new ClassRosterRowDto.ParentInfo(
                            parent.getId(),
                            parentUser.getId(),
                            parentUser.getFirstName(),
                            parentUser.getLastName(),
                            parentUser.getEmail(),
                            parentUser.getPhone(),
                            ps.getRelationship(),
                            parentUser.getEmailVerified(),
                            parentUser.getPasswordSet()
                    );
                })
                .toList();

        List<Grade> grades = gradeRepository.findByStudentIdOrderByGradeDateAsc(studentId).stream()
                .filter(g -> org.afritechinnovations.service.academic.SelectedAcademicYear.matches(g.getClassSubjectTeacher().getSchoolClass().getAcademicYear())).toList();
        List<StudentDetailDto.GradeInfo> gradeInfos = grades.stream()
                .map(grade -> {
                    ClassSubjectTeacher cst = grade.getClassSubjectTeacher();
                    return new StudentDetailDto.GradeInfo(
                            grade.getId(),
                            cst.getSubject().getName(),
                            cst.getTeacher().getUser().getFirstName() + " " + cst.getTeacher().getUser().getLastName(),
                            grade.getTerm(),
                            grade.getType(),
                            grade.getValue(),
                            grade.getMaxValue(),
                            grade.getGradeDate()
                    );
                })
                .sorted(Comparator.comparing(StudentDetailDto.GradeInfo::gradeDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        java.util.OptionalDouble averageOpt = grades.stream()
                .filter(g -> g.getValue() != null && g.getMaxValue() != null
                        && g.getMaxValue().compareTo(BigDecimal.ZERO) != 0)
                .mapToDouble(g -> g.getValue().doubleValue() / g.getMaxValue().doubleValue() * 20)
                .average();
        Double overallAverage = averageOpt.isPresent() ? averageOpt.getAsDouble() : null;

        List<Attendance> attendances = attendanceRepository.findByStudentIdOrderByAttendanceDateDesc(studentId).stream()
                .filter(a -> org.afritechinnovations.service.academic.SelectedAcademicYear.matches(a.getSchoolClass().getAcademicYear())).toList();
        long present = attendances.stream().filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.PRESENT).count();
        long late = attendances.stream().filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.LATE).count();
        // Une absence est "justifiée" si son statut est EXCUSED ou si un motif de justification a été renseigné.
        long justifiedAbsences = attendances.stream()
                .filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.ABSENT
                        || a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.EXCUSED)
                .filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.EXCUSED
                        || (a.getJustification() != null && !a.getJustification().isBlank()))
                .count();
        long absent = attendances.stream()
                .filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.ABSENT
                        || a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.EXCUSED)
                .count();
        long unjustifiedAbsences = absent - justifiedAbsences;
        long total = attendances.size();
        double attendanceRate = total > 0 ? (present * 100.0 / total) : 0.0;

        StudentDetailDto.AttendanceSummary summary = new StudentDetailDto.AttendanceSummary(
                total, present, absent, late, justifiedAbsences, Math.max(unjustifiedAbsences, 0), attendanceRate);

        List<StudentDetailDto.AttendanceInfo> recentAttendance = attendances.stream()
                .limit(30)
                .map(a -> new StudentDetailDto.AttendanceInfo(a.getId(), a.getAttendanceDate(), a.getStatus().name(), a.getJustification()))
                .toList();

        List<StudentDetailDto.InvoiceInfo> invoiceInfos = buildInvoiceInfos(studentId);
        StudentDetailDto.BillingSummary billingSummary = buildBillingSummary(invoiceInfos);

        return new StudentDetailDto(
                student.getId(),
                user.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getEmail(),
                student.getPhone(),
                student.getRegistrationNumber(),
                student.getBirthDate(),
                student.getGender(),
                student.getSchool().getName(),
                student.getSchool().getId(),
                className,
                parents,
                gradeInfos,
                overallAverage,
                summary,
                recentAttendance,
                invoiceInfos,
                billingSummary
        );
    }

    public StudentDetailDto.AttendanceInfo addAttendance(Long studentId, UpsertAttendanceRequest request,
                                                          Long ownerId, boolean systemAdmin) {
        Student student = requireOwnedStudent(studentId, ownerId, systemAdmin);
        StudentEnrollment activeEnrollment = studentEnrollmentRepository.findByStudentId(studentId).stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cet élève n'a pas de classe active"));

        String justification = request.getJustification();
        if ((justification == null || justification.isBlank())
                && (request.getStatus() == AttendanceStatus.ABSENT || request.getStatus() == AttendanceStatus.LATE)) {
            // Une absence signalée par le parent et acceptée par l'école justifie automatiquement la saisie.
            justification = absenceReportRepository.findCovering(studentId, request.getAttendanceDate(),
                            AbsenceReportStatus.ACKNOWLEDGED).stream()
                    .findFirst()
                    .map(r -> AbsenceReportJustification.of(r.getReason()))
                    .orElse(justification);
        }
        Attendance attendance = attendanceRepository.findByStudentIdAndSchoolClassIdAndAttendanceDate(
                studentId,activeEnrollment.getSchoolClass().getId(),request.getAttendanceDate())
                .orElseGet(() -> Attendance.builder().student(student).schoolClass(activeEnrollment.getSchoolClass())
                        .attendanceDate(request.getAttendanceDate()).build());
        attendance.setStatus(request.getStatus());
        attendance.setJustification(justification);
        attendance = attendanceRepository.save(attendance);
        return toAttendanceInfo(attendance);
    }

    public StudentDetailDto.AttendanceInfo updateAttendance(Long studentId, Long attendanceId,
                                                             UpsertAttendanceRequest request,
                                                             Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new IllegalArgumentException("Présence introuvable: " + attendanceId));
        if (!attendance.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette présence n'appartient pas à cet élève");
        }
        attendance.setAttendanceDate(request.getAttendanceDate());
        attendance.setStatus(request.getStatus());
        attendance.setJustification(request.getJustification());
        attendance = attendanceRepository.save(attendance);
        return toAttendanceInfo(attendance);
    }

    public void deleteAttendance(Long studentId, Long attendanceId, Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new IllegalArgumentException("Présence introuvable: " + attendanceId));
        if (!attendance.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette présence n'appartient pas à cet élève");
        }
        attendanceRepository.deleteById(attendanceId);
    }

    public StudentDetailDto.InvoiceInfo createInvoice(Long studentId, CreateStudentInvoiceRequest request,
                                                       Long ownerId, boolean systemAdmin) {
        Student student = requireOwnedStudent(studentId, ownerId, systemAdmin);
        FeeType feeType = feeTypeRepository.findById(request.getFeeTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Type de frais introuvable: " + request.getFeeTypeId()));
        if (!feeType.getSchool().getId().equals(student.getSchool().getId())) {
            throw new AccessDeniedException("Ce type de frais n'appartient pas à l'établissement de l'élève");
        }
        if (!feeType.isActive()) {
            throw new IllegalArgumentException("Ce type de frais est archivé");
        }

        AcademicYear academicYear = request.getAcademicYearId() != null
                ? academicYearRepository.findById(request.getAcademicYearId())
                        .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable"))
                : academicYearRepository.findBySchoolIdAndIsCurrentTrue(student.getSchool().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Aucune année scolaire courante définie pour cet établissement"));

        Invoice invoice = Invoice.builder()
                .student(student)
                .feeType(feeType)
                .academicYear(academicYear)
                .amountDue(request.getAmountDue() != null ? request.getAmountDue() : feeType.getAmount())
                .dueDate(request.getDueDate())
                .status(InvoiceStatus.PENDING)
                .build();
        invoice = invoiceRepository.save(invoice);
        return toInvoiceInfo(invoice, List.of());
    }

    public StudentDetailDto.InvoiceInfo addPayment(Long studentId, Long invoiceId, CreateStudentPaymentRequest request,
                                                    Long ownerId, boolean systemAdmin) {
        Invoice invoice = requireOwnedInvoice(studentId, invoiceId, ownerId, systemAdmin);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Impossible d'encaisser un frais annulé");
        }
        validatePaymentAmount(request.getAmount(), invoice, null);

        Payment payment = Payment.builder()
                .invoice(invoice)
                .amount(request.getAmount())
                .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now())
                .method(request.getMethod())
                .reference(generatePaymentReference())
                .build();
        paymentRepository.save(payment);

        List<Payment> payments = paymentRepository.findByInvoiceId(invoiceId);
        updateInvoicePaymentStatus(invoice, payments);
        return toInvoiceInfo(invoice, payments);
    }

    public StudentDetailDto.InvoiceInfo updatePayment(Long studentId, Long invoiceId, Long paymentId,
                                                       CreateStudentPaymentRequest request, Long ownerId, boolean systemAdmin) {
        Invoice invoice = requireOwnedInvoice(studentId, invoiceId, ownerId, systemAdmin);
        Payment payment = requireInvoicePayment(invoiceId, paymentId);
        validatePaymentAmount(request.getAmount(), invoice, paymentId);
        payment.setAmount(request.getAmount());
        payment.setPaymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : payment.getPaymentDate());
        payment.setMethod(request.getMethod());
        paymentRepository.save(payment);
        List<Payment> payments = paymentRepository.findByInvoiceId(invoiceId);
        updateInvoicePaymentStatus(invoice, payments);
        return toInvoiceInfo(invoice, payments);
    }

    public void deletePayment(Long studentId, Long invoiceId, Long paymentId, Long ownerId, boolean systemAdmin) {
        Invoice invoice = requireOwnedInvoice(studentId, invoiceId, ownerId, systemAdmin);
        Payment payment = requireInvoicePayment(invoiceId, paymentId);
        paymentRepository.delete(payment);
        updateInvoicePaymentStatus(invoice, paymentRepository.findByInvoiceId(invoiceId));
    }

    private Invoice requireOwnedInvoice(Long studentId, Long invoiceId, Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        invoiceRepository.lockForPayment(invoiceId);
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable: " + invoiceId));
        if (!invoice.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette facture n'appartient pas à cet élève");
        }
        return invoice;
    }

    private Payment requireInvoicePayment(Long invoiceId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable: " + paymentId));
        if (!payment.getInvoice().getId().equals(invoiceId)) {
            throw new IllegalArgumentException("Ce paiement n'appartient pas à ce frais");
        }
        return payment;
    }

    private void validatePaymentAmount(BigDecimal amount, Invoice invoice, Long excludedPaymentId) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à zéro");
        }
        BigDecimal otherPayments = paymentRepository.findByInvoiceId(invoice.getId()).stream()
                .filter(payment -> !payment.getId().equals(excludedPaymentId))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (otherPayments.add(amount).compareTo(invoice.netAmount()) > 0) {
            throw new IllegalArgumentException("Le total payé ne peut pas dépasser le montant dû");
        }
    }

    private void updateInvoicePaymentStatus(Invoice invoice, List<Payment> payments) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            return;
        }
        BigDecimal totalPaid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setStatus(totalPaid.compareTo(invoice.netAmount()) >= 0 ? InvoiceStatus.PAID
                : invoice.getDueDate().isBefore(LocalDate.now()) ? InvoiceStatus.OVERDUE : InvoiceStatus.PENDING);
        invoiceRepository.save(invoice);
    }

    public void deleteInvoice(Long studentId, Long invoiceId, Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable: " + invoiceId));
        if (!invoice.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette facture n'appartient pas à cet élève");
        }
        invoiceRepository.deleteById(invoiceId);
    }

    public StudentDetailDto.InvoiceInfo cancelInvoice(Long studentId, Long invoiceId, Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable: " + invoiceId));
        if (!invoice.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette facture n'appartient pas à cet élève");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice = invoiceRepository.save(invoice);
        return toInvoiceInfo(invoice, paymentRepository.findByInvoiceId(invoiceId));
    }

    private synchronized String generatePaymentReference() {
        String yearPrefix = String.valueOf(LocalDate.now().getYear());
        long countThisYear = paymentRepository.countByReferenceStartingWith(yearPrefix + "-");
        String reference;
        do {
            reference = yearPrefix + "-" + String.format("%04d", ++countThisYear);
        } while (paymentRepository.existsByReference(reference));
        return reference;
    }

    private Student requireOwnedStudent(Long studentId, Long ownerId, boolean systemAdmin) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        if (!systemAdmin && !student.getSchool().getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(student.getSchool().getId(), ownerId, StaffModule.STUDENTS)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les élèves de votre établissement");
        }
        return student;
    }

    private StudentDetailDto.AttendanceInfo toAttendanceInfo(Attendance attendance) {
        return new StudentDetailDto.AttendanceInfo(
                attendance.getId(), attendance.getAttendanceDate(), attendance.getStatus().name(), attendance.getJustification());
    }

    private List<StudentDetailDto.InvoiceInfo> buildInvoiceInfos(Long studentId) {
        List<Invoice> invoices = invoiceRepository.findByStudentId(studentId);
        Long yearId = invoices.isEmpty() ? null : org.afritechinnovations.service.academic.SelectedAcademicYear.id(invoices.get(0).getStudent().getSchool().getId());
        Set<Long> carried = yearId == null ? Set.of() : new java.util.HashSet<>(invoiceRepository.carriedInvoiceIds(yearId));
        return invoices.stream()
                .filter(i -> org.afritechinnovations.service.academic.SelectedAcademicYear.matches(i.getAcademicYear()) || carried.contains(i.getId()))
                .map(invoice -> toInvoiceInfo(invoice, paymentRepository.findByInvoiceId(invoice.getId())))
                .sorted(Comparator.comparing(StudentDetailDto.InvoiceInfo::dueDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private StudentDetailDto.InvoiceInfo toInvoiceInfo(Invoice invoice, List<Payment> payments) {
        BigDecimal totalPaid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = invoice.getStatus() == InvoiceStatus.CANCELLED
                ? BigDecimal.ZERO : invoice.netAmount().subtract(totalPaid);
        List<StudentDetailDto.PaymentInfo> paymentInfos = payments.stream()
                .map(p -> new StudentDetailDto.PaymentInfo(p.getId(), p.getAmount(), p.getPaymentDate(),
                        p.getMethod().name(), p.getReference()))
                .toList();
        return new StudentDetailDto.InvoiceInfo(
                invoice.getId(),
                invoice.getFeeType().getId(),
                invoice.getFeeType().getName(),
                invoice.getAmountDue(),
                invoice.getDueDate(),
                invoice.getStatus().name(),
                totalPaid,
                balance,
                paymentInfos,
                invoice.getDiscountAmount() == null ? BigDecimal.ZERO : invoice.getDiscountAmount(),
                invoice.getDiscountReason()
        );
    }

    private StudentDetailDto.BillingSummary buildBillingSummary(List<StudentDetailDto.InvoiceInfo> invoices) {
        BigDecimal totalDue = invoices.stream()
                .filter(invoice -> !InvoiceStatus.CANCELLED.name().equals(invoice.status()))
                .map(invoice -> invoice.amountDue().subtract(invoice.discountAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = invoices.stream().map(StudentDetailDto.InvoiceInfo::totalPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBalance = invoices.stream().map(StudentDetailDto.InvoiceInfo::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StudentDetailDto.BillingSummary(totalDue, totalPaid, totalBalance);
    }

    private SchoolClass requireOwnedClass(Long classId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + classId));
        if (!systemAdmin && !schoolClass.getSchool().getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(schoolClass.getSchool().getId(), ownerId, StaffModule.STUDENTS)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que les classes de votre établissement");
        }
        return schoolClass;
    }

    private void requireUnactivatedStudentEmailAccess(User account, Long actor, boolean systemAdmin) {
        if (account.isOwnerAccount() || !account.getPlatformRoles().isEmpty())
            throw new IllegalArgumentException("Ce compte possède un accès propriétaire ou administrateur. Son courriel ne peut pas être corrigé depuis une fiche élève.");
        var memberships = schoolUserRepository.findByUserId(account.getId());
        if (memberships.stream().anyMatch(link -> !"STUDENT".equals(link.getRole().getName())))
            throw new IllegalArgumentException("Ce compte possède aussi un autre rôle. Contactez le support pour vérifier son identité avant de corriger son courriel.");
        var dossiers = studentRepository.findAllByUserId(account.getId());
        if (!systemAdmin && (memberships.stream().anyMatch(link -> !ownsSchool(link.getSchool(), actor))
                || dossiers.stream().anyMatch(dossier -> !ownsSchool(dossier.getSchool(), actor))))
            throw new IllegalArgumentException("Ce compte est également lié à un établissement qui ne vous appartient pas. Le support doit vérifier la correction de son courriel commun.");
    }

    private boolean ownsSchool(org.afritechinnovations.model.common.School school, Long actor) {
        return school != null && school.getOwner() != null && actor.equals(school.getOwner().getId());
    }

    private String sharedIdentityError(User user, String role, Long schoolId) {
        if (user.isOwnerAccount() || !user.getPlatformRoles().isEmpty()) {
            return "Ce compte dispose aussi d’un accès propriétaire ou administrateur. Son identité ne peut pas être modifiée depuis cette fiche. Pour corriger son courriel, contactez le support technique.";
        }
        var memberships = schoolUserRepository.findByUserId(user.getId());
        if (memberships.stream().anyMatch(link -> !role.equals(link.getRole().getName()))) {
            return "Ce compte possède plusieurs rôles. Son courriel et son identité sont communs à ses différents accès et ne peuvent pas être modifiés depuis cette fiche. Pour corriger son courriel, contactez le support technique.";
        }
        if (memberships.stream().anyMatch(link -> !schoolId.equals(link.getSchool().getId()))) {
            return "Ce compte est partagé entre plusieurs établissements. Modifier son courriel ou son identité affecterait tous ses dossiers. Cette modification n’est pas autorisée depuis une seule fiche. Pour corriger son courriel, contactez le support technique.";
        }
        return null;
    }

    private boolean identityChanged(User user, String firstName, String lastName, String email, String phone) {
        return !java.util.Objects.equals(email, user.getEmail() == null ? null : user.getEmail().toLowerCase(java.util.Locale.ROOT))
                || !java.util.Objects.equals(firstName.trim(), user.getFirstName())
                || !java.util.Objects.equals(lastName.trim(), user.getLastName())
                || !java.util.Objects.equals(phone, user.getPhone());
    }

    private List<String> teacherNamesForClass(Long classId) {
        return classSubjectTeacherRepository.findAllWithTeacherByClassId(classId)
                .stream()
                .filter(ClassSubjectTeacher::isActive)
                .map(ClassSubjectTeacher::getTeacher)
                .map(teacher -> teacher.getUser().getFirstName() + " " + teacher.getUser().getLastName())
                .distinct()
                .toList();
    }

    private ClassRosterRowDto toRow(Student student, List<String> teacherNames) {
        return toRow(student, teacherNames, false);
    }

    private ClassRosterRowDto toRow(Student student, List<String> teacherNames, boolean withBilling) {
        User user = student.getUser();
        List<ClassRosterRowDto.ParentInfo> parents = parentStudentRepository
                .findByStudentIdWithParentUser(student.getId())
                .stream()
                .map(ps -> {
                    Parent parent = ps.getParent();
                    User parentUser = parent.getUser();
                    return new ClassRosterRowDto.ParentInfo(
                            parent.getId(),
                            parentUser.getId(),
                            parentUser.getFirstName(),
                            parentUser.getLastName(),
                            parentUser.getEmail(),
                            parentUser.getPhone(),
                            ps.getRelationship(),
                            parentUser.getEmailVerified(),
                            parentUser.getPasswordSet()
                    );
                })
                .toList();

        return new ClassRosterRowDto(
                student.getId(),
                user.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getEmail(),
                student.getPhone(),
                student.getRegistrationNumber(),
                student.getBirthDate(),
                student.getGender(),
                parents,
                teacherNames,
                user.getEmailVerified(),
                invitations.deliveryStatus(user.getId()),
                withBilling ? buildBillingSummary(buildInvoiceInfos(student.getId())) : null,
                user.getEmail(), user.getPasswordSet()
        );
    }
}
