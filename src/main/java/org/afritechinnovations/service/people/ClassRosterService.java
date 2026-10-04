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

    public List<ClassRosterRowDto> getRoster(Long classId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        List<String> teacherNames = teacherNamesForClass(classId);

        return studentEnrollmentRepository
                .findStudentsWithUserByClassIdAndStatusIn(classId,
                        List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))
                .stream()
                .map(StudentEnrollment::getStudent)
                .filter(student -> student.getSchool().getId().equals(schoolClass.getSchool().getId()))
                .map(student -> toRow(student, teacherNames))
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

        User user = userRepository.findById(student.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable"));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        String studentEmail = request.getEmail().trim().toLowerCase();
        boolean emailChanged = !studentEmail.equalsIgnoreCase(String.valueOf(user.getEmail()));
        if (emailChanged) {
            user.setEmailVerified(false);
            user.revokeSessions();
        }
        user.setEmail(studentEmail);
        user.setPhone(request.getPhone());
        userRepository.save(user);

        if (emailChanged) invitations.sendInvitation(user);

        student.setRegistrationNumber(request.getRegistrationNumber().trim());
        student.setBirthDate(request.getBirthDate());
        student.setGender(request.getGender());
        studentRepository.save(student);

        return toRow(student, teacherNamesForClass(classId));
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
        if (!parentStudentRepository.findByStudentIdWithParentUser(studentId).isEmpty()) {
            throw new IllegalArgumentException("Un parent est déjà associé à cet élève. Actualisez la liste.");
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
        if (parentEmail != null && !parentEmail.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmailIgnoreCase(parentEmail)) {
            throw new IllegalArgumentException("Un utilisateur existe déjà avec cet email: " + parentEmail);
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
        // Supprime le compte utilisateur de l'élève : la suppression est répercutée en cascade
        // (students, student_enrollments, parent_student, grades, attendances, report_cards, invoices).
        userRepository.deleteById(student.getUser().getId());
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
     * Inscrit un nouvel élève, lui facture les frais choisis pour l'année de la classe et encaisse
     * immédiatement les montants versés (une référence de reçu par paiement). Droits vérifiés par l'appelant.
     */
    public OwnerEnrollmentDto.RegistrationResult enrollNewStudentWithFees(SchoolClass schoolClass,
                                                                         NewStudentEnrollmentRequest request) {
        List<OwnerEnrollmentDto.FeeLine> lines = request.getFees() == null ? List.of() : request.getFees();
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
        String email = request.getEmail().trim().toLowerCase();
        User pending = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if ((pending != null || userRepository.existsByEmailIgnoreCase(email)) && (pending == null || Boolean.TRUE.equals(pending.getApproved())
                || !Boolean.TRUE.equals(pending.getActive()) || pending.getRequestedRole() != org.afritechinnovations.model.common.RoleName.STUDENT
                || !schoolClass.getSchool().getId().equals(pending.getRequestedSchoolId()))) {
            throw new IllegalArgumentException("Un utilisateur existe déjà avec cet email: " + email);
        }
        if (pending != null && studentRepository.findAllByUserId(pending.getId()).stream().anyMatch(t -> t.getSchool().getId().equals(schoolClass.getSchool().getId()))) {
            throw new IllegalArgumentException("Ce compte possède déjà un dossier élève dans cet établissement");
        }
        String registrationNumber = request.getRegistrationNumber() == null ? "" : request.getRegistrationNumber().trim();
        if (registrationNumber.isEmpty()) {
            registrationNumber = nextRegistrationNumber(schoolClass);
        } else if (studentRepository.findBySchoolIdAndRegistrationNumber(schoolClass.getSchool().getId(),
                registrationNumber).isPresent()) {
            throw new IllegalArgumentException("Ce matricule est déjà utilisé dans cet établissement");
        }

        User user = pending != null ? pending : User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .passwordSet(false)
                .phone(request.getPhone())
                .active(true)
                .approved(true)
                .build();
        user = userRepository.save(user);

        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("Rôle non configuré: STUDENT"));
        schoolUserRepository.save(SchoolUser.builder()
                .user(user)
                .school(schoolClass.getSchool())
                .role(studentRole)
                .build());

        Student student = Student.builder()
                .user(user)
                .school(schoolClass.getSchool())
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

        invitations.sendInvitation(user);

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
            if (user != null && studentRepository.findByUserId(user.getId()).isPresent()) {
                throw new IllegalArgumentException("Le courriel " + email + " appartient déjà à un élève");
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
            } else if (user.getPhone() == null && phone != null) {
                user.setPhone(phone);
                userRepository.save(user);
            }

            User parentUser = user;
            Parent parent = parentRepository.findByUserId(parentUser.getId())
                    .orElseGet(() -> parentRepository.save(Parent.builder().user(parentUser).build()));
            linkGuardian(student, parent, guardian.relationship(), parentRole, linked);
            if (email != null && !Boolean.TRUE.equals(parentUser.getEmailVerified())) invitations.sendInvitation(parentUser);
        }
    }

    private void linkGuardian(Student student, Parent parent, String relationship, Role parentRole, Set<Long> linked) {
        if (!linked.add(parent.getId())) {
            return;
        }
        User parentUser = parent.getUser();
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
                            ps.getRelationship()
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
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
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
                            ps.getRelationship()
                    );
                })
                .toList();

        return new ClassRosterRowDto(
                student.getId(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                student.getRegistrationNumber(),
                student.getBirthDate(),
                student.getGender(),
                parents,
                teacherNames,
                user.getEmailVerified(),
                invitations.deliveryStatus(user.getId())
        );
    }
}
