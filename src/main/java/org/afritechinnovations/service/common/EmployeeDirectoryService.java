package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.EmployeeDirectoryDto.*;
import org.afritechinnovations.dto.common.OwnerStaffDto;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.service.auth.EmailVerificationService;
import org.afritechinnovations.service.auth.PasswordResetService;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class EmployeeDirectoryService {
    private final SchoolRepository schools;
    private final SchoolStaffRepository staff;
    private final TeacherRepository teachers;
    private final UserRepository users;
    private final SchoolUserRepository links;
    private final RoleRepository roles;
    private final TeacherWorkService teacherWork;
    private final JdbcTemplate jdbc;
    private final EmailVerificationService invitations;
    private final PasswordResetService resets;

    @Transactional(readOnly=true)
    public List<Row> list(Long schoolId,Long actor,boolean admin) {
        School school=authorize(schoolId,actor,admin);
        Map<Long,SchoolStaff> staffByUser=new LinkedHashMap<>();
        staff.findBySchoolWithUser(schoolId).forEach(s->staffByUser.put(s.getUser().getId(),s));
        Map<Long,Teacher> teachersByUser=new LinkedHashMap<>();
        teachers.findBySchoolId(schoolId).forEach(t->teachersByUser.put(t.getUser().getId(),t));
        Map<Long,Map<String,Object>> rates=new HashMap<>();
        jdbc.queryForList("""
            SELECT DISTINCT ON (teacher_id) teacher_id,rate_type,amount,effective_from FROM teacher_rates
            WHERE teacher_id IN (SELECT id FROM teachers WHERE school_id=?) AND effective_from<=?
            ORDER BY teacher_id,effective_from DESC,id DESC
            """,schoolId,YearMonth.now().atDay(1)).forEach(r->rates.put(((Number)r.get("teacher_id")).longValue(),r));
        Set<Long> ids=new LinkedHashSet<>(staffByUser.keySet());ids.addAll(teachersByUser.keySet());
        return ids.stream().map(id->row(school,staffByUser.get(id),teachersByUser.get(id),rates,actor,admin))
                .sorted(Comparator.comparing((Row r)->r.staff().lastName(),String.CASE_INSENSITIVE_ORDER).thenComparing(r->r.staff().firstName(),String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Row update(Long schoolId,Long userId,Update request,Long actor,boolean admin) {
        School school=authorize(schoolId,actor,admin);
        Teacher teacher=teachers.findBySchoolId(schoolId).stream().filter(t->t.getUser().getId().equals(userId)).findFirst().orElse(null);
        if(teacher!=null) teacher=teachers.findByIdForUpdate(teacher.getId()).orElseThrow();
        SchoolStaff member=staff.findBySchoolIdAndUserId(schoolId,userId).orElse(null);
        if(member==null && teacher==null) throw new IllegalArgumentException("Cet employé n’appartient pas à l’établissement.");
        User user=users.findById(userId).orElseThrow();
        var profile=request.profile();
        if(school.getOwner().getId().equals(userId) && !profile.modules().equals(EnumSet.allOf(StaffModule.class)))
            throw new IllegalArgumentException("Les accès du propriétaire couvrent tous les modules et ne peuvent pas être retirés depuis le personnel.");
        boolean identityChanged=!Objects.equals(user.getFirstName(),profile.firstName().trim()) || !Objects.equals(user.getLastName(),profile.lastName().trim()) || !Objects.equals(user.getPhone(),clean(profile.phone()));
        boolean emailChanged=!Objects.equals(normalize(user.getEmail()),normalize(profile.email()));
        if((identityChanged || emailChanged) && !identityEditable(user,actor,admin))
            throw new AccessDeniedException("L’identité de ce compte propriétaire ou administrateur se modifie depuis son propre profil.");
        if(emailChanged) {
            String email=normalize(profile.email());
            if(email!=null && users.findByEmailIgnoreCase(email).filter(u->!u.getId().equals(userId)).isPresent())
                throw new IllegalArgumentException("Ce courriel est déjà utilisé par un autre compte. Les comptes ne sont pas fusionnés.");
            user.setEmail(email);user.setEmailVerified(false);user.revokeSessions();
            jdbc.update("DELETE FROM email_verification_tokens WHERE user_id=?",userId);
            jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?",userId);
        }
        user.setFirstName(profile.firstName().trim());user.setLastName(profile.lastName().trim());user.setPhone(clean(profile.phone()));
        users.save(user);
        if(teacher!=null) {
            String number=clean(request.employeeNumber());
            if(number==null) throw new IllegalArgumentException("Le numéro d’employé de l’enseignant est obligatoire.");
            Long teacherId=teacher.getId();
            if(teachers.findBySchoolIdAndEmployeeNumber(schoolId,number).filter(t->!t.getId().equals(teacherId)).isPresent())
                throw new IllegalArgumentException("Ce numéro d’employé est déjà utilisé dans l’établissement.");
            if(!Objects.equals(teacher.getHireDate(),request.hireDate()) && request.hireDate()!=null) {
                Integer previous=jdbc.queryForObject("""
                    SELECT (SELECT count(*) FROM teacher_payments WHERE teacher_id=? AND pay_month<?)
                         + (SELECT count(*) FROM school_payables WHERE teacher_id=? AND period<?)
                    """,Integer.class,teacherId,request.hireDate().withDayOfMonth(1),teacherId,request.hireDate().withDayOfMonth(1));
                if(previous!=null && previous>0) throw new IllegalArgumentException("La date d’embauche ne peut pas exclure un mois de salaire déjà préparé ou payé.");
            }
            teacher.setEmployeeNumber(number);teacher.setSpecialty(clean(request.specialty()));teacher.setHireDate(request.hireDate());teachers.save(teacher);
            if(request.teacherRate()!=null) teacherWork.addRate(teacherId,request.teacherRate(),actor,true);
        } else if(request.teacherRate()!=null) throw new IllegalArgumentException("Cet employé n’a pas de fiche enseignant.");
        if(member!=null && !member.isAccessOnly() && !request.administrativeEmployment())
            throw new IllegalArgumentException("Conservez la fonction du personnel ; ses accès peuvent être retirés sans supprimer son historique de paie.");
        if(member==null && (!profile.modules().isEmpty() || request.administrativeEmployment())) {
            member=SchoolStaff.builder().school(school).user(user).jobTitle(profile.jobTitle().trim()).createdBy(users.getReferenceById(actor)).accessOnly(!request.administrativeEmployment()).build();
            ensureRole(user,school);
        }
        if(member!=null) {
            member.setAccessOnly(!request.administrativeEmployment());member.setJobTitle(profile.jobTitle().trim());
            member.setMonthlySalary(member.isAccessOnly()?null:profile.monthlySalary());
            member.getModules().clear();member.getModules().addAll(profile.modules());member.setUpdatedAt(LocalDateTime.now());staff.saveAndFlush(member);
        }
        users.flush();
        if(emailChanged && user.getEmail()!=null) invitations.sendInvitation(user);
        return list(schoolId,actor,admin).stream().filter(r->r.staff().userId().equals(userId)).findFirst().orElseThrow();
    }

    public OwnerStaffDto.PasswordReset sendLink(Long schoolId,Long userId,Long actor,boolean admin) {
        var person=list(schoolId,actor,admin).stream().filter(r->r.staff().userId().equals(userId)).findFirst().orElseThrow(()->new IllegalArgumentException("Employé introuvable."));
        User user=users.findById(userId).orElseThrow();
        if(!person.emailEditable()) throw new AccessDeniedException("Le titulaire de ce compte utilise la récupération depuis la page de connexion.");
        if(user.getEmail()==null) throw new IllegalArgumentException("Renseignez d’abord le courriel de cet employé.");
        boolean sent=!Boolean.TRUE.equals(user.getPasswordSet()) || !Boolean.TRUE.equals(user.getEmailVerified())?invitations.sendInvitation(user):resets.sendManagedReset(user);
        return new OwnerStaffDto.PasswordReset(null,sent);
    }

    private Row row(School school,SchoolStaff member,Teacher teacher,Map<Long,Map<String,Object>> rates,Long actor,boolean admin) {
        User user=member==null?teacher.getUser():member.getUser();boolean owner=school.getOwner().getId().equals(user.getId());
        List<String> modules=owner?Arrays.stream(StaffModule.values()).map(Enum::name).toList():member==null?List.of():Arrays.stream(StaffModule.values()).filter(member.getModules()::contains).map(Enum::name).toList();
        var base=new OwnerStaffDto.StaffRow(member==null?null:member.getId(),user.getId(),user.getFirstName(),user.getLastName(),user.getEmail(),user.getPhone(),
                member==null?"Enseignant":member.getJobTitle(),modules,owner || member==null || member.isActive(),false,
                member==null?user.getCreatedAt():member.getCreatedAt(),user.getEmailVerified(),invitations.deliveryStatus(user.getId()),member==null?null:member.getMonthlySalary());
        TeacherProfile profile=null;
        if(teacher!=null) {
            var rate=rates.get(teacher.getId());
            profile=new TeacherProfile(teacher.getId(),teacher.getEmployeeNumber(),teacher.getSpecialty(),teacher.getHireDate(),
                    rate==null?teacher.getMonthlySalary()==null?null:"FIXED_MONTHLY":rate.get("rate_type").toString(),
                    rate==null?teacher.getMonthlySalary():(java.math.BigDecimal)rate.get("amount"),rate==null?null:((java.sql.Date)rate.get("effective_from")).toLocalDate());
        }
        boolean editable=identityEditable(user,actor,admin);
        return new Row(base,profile,member!=null&&!member.isAccessOnly(),owner,editable,editable,Boolean.TRUE.equals(user.getActive()));
    }
    private boolean identityEditable(User user,Long actor,boolean admin) {
        return admin || user.getId().equals(actor) || !user.isOwnerAccount() && user.getPlatformRoles().isEmpty() && schools.findByOwnerId(user.getId()).isEmpty()
                && links.findByUserId(user.getId()).stream().noneMatch(link->Set.of("SUPER_ADMIN","SCHOOL_ADMIN").contains(link.getRole().getName()));
    }
    private School authorize(Long schoolId,Long actor,boolean admin) {
        School school=schools.findById(schoolId).orElseThrow(()->new IllegalArgumentException("Établissement introuvable."));
        if(!admin && (school.getOwner()==null || !school.getOwner().getId().equals(actor)))
            throw new AccessDeniedException("Seul le propriétaire peut gérer les employés et leurs accès.");
        return school;
    }
    private void ensureRole(User user,School school) {
        if(links.findByUserId(user.getId()).stream().noneMatch(l->l.getSchool().getId().equals(school.getId()) && l.getRole().getName().equals("STAFF")))
            links.save(SchoolUser.builder().school(school).user(user).role(roles.findByName("STAFF").orElseThrow()).build());
    }
    private static String clean(String value){return value==null || value.isBlank()?null:value.trim();}
    private static String normalize(String value){return value==null || value.isBlank()?null:value.trim().toLowerCase(Locale.ROOT);}
}
