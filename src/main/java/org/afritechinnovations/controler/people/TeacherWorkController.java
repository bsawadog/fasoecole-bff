package org.afritechinnovations.controler.people;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.AssignClassTeacherRequest;
import org.afritechinnovations.dto.people.CreateClassTeacherRequest;
import org.afritechinnovations.dto.people.TeacherExtraRequest;
import org.afritechinnovations.dto.people.TeacherPaymentRequest;
import org.afritechinnovations.dto.people.TeacherRateRequest;
import org.afritechinnovations.dto.people.TeacherSessionRequest;
import org.afritechinnovations.dto.people.TeacherSlotRequest;
import org.afritechinnovations.dto.people.TeacherWorkDto;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Gestion des enseignants par classe et paie mensuelle, réservée au propriétaire (SCHOOL_ADMIN) ou SUPER_ADMIN. */
@RestController
@RequestMapping("/api/teacher-work")
@RequiredArgsConstructor
public class TeacherWorkController {

    private final TeacherWorkService teacherWorkService;

    @GetMapping("/classes/{classId}/teachers")
    public List<TeacherWorkDto.TeacherInfo> listClassTeachers(@PathVariable Long classId, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.listClassTeachers(classId, principal.getId(), isSuperAdmin(principal));
    }

    @GetMapping("/schools/{schoolId}/teachers")
    public List<TeacherWorkDto.TeacherInfo> listSchoolTeachers(@PathVariable Long schoolId, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.listSchoolTeachers(schoolId, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/schools/{schoolId}/teachers")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherWorkDto.TeacherInfo createSchoolTeacher(@PathVariable Long schoolId,
            @Valid @RequestBody org.afritechinnovations.dto.people.CreateTeacherRequest request, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return teacherWorkService.createSchoolTeacher(schoolId,request,p.getId(),isSuperAdmin(p));
    }

    @GetMapping("/classes/{classId}/candidates")
    public List<TeacherWorkDto.TeacherInfo> listClassCandidates(@PathVariable Long classId, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.listClassCandidates(classId, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/classes/{classId}/teachers")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherWorkDto.TeacherInfo createClassTeacher(@PathVariable Long classId,
                                                         @Valid @RequestBody CreateClassTeacherRequest request,
                                                         Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.createClassTeacher(classId, request, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/classes/{classId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherWorkDto.TeacherInfo assignClassTeacher(@PathVariable Long classId,
                                                         @Valid @RequestBody AssignClassTeacherRequest request,
                                                         Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.assignClassTeacher(classId, request, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/classes/{classId}/teachers/{teacherId}/deactivate")
    public TeacherWorkDto.TeacherInfo deactivateClassTeacher(@PathVariable Long classId, @PathVariable Long teacherId,
                                                             Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.deactivateClassTeacher(classId, teacherId, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/classes/{classId}/teachers/{teacherId}/reactivate")
    public TeacherWorkDto.TeacherInfo reactivateClassTeacher(@PathVariable Long classId, @PathVariable Long teacherId,
                                                             Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.reactivateClassTeacher(classId, teacherId, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/teachers/{teacherId}/send-summary")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendSummary(@PathVariable Long teacherId, @RequestParam(required = false) String month,
                            Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        teacherWorkService.sendTeacherSummary(teacherId, month, principal.getId(), isSuperAdmin(principal));
    }

    @GetMapping("/teachers/{teacherId}")
    public TeacherWorkDto.TeacherDetail getTeacher(@PathVariable Long teacherId,
                                                   @RequestParam(required = false) String month,
                                                   Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.getTeacherDetail(teacherId, month, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/teachers/{teacherId}/rates")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherWorkDto.RateInfo addRate(@PathVariable Long teacherId, @Valid @RequestBody TeacherRateRequest request,
                                           Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.addRate(teacherId, request, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/teachers/{teacherId}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherWorkDto.SlotInfo addSlot(@PathVariable Long teacherId, @Valid @RequestBody TeacherSlotRequest request,
                                           Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.addSlot(teacherId, request, principal.getId(), isSuperAdmin(principal));
    }

    @DeleteMapping("/teachers/{teacherId}/slots/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveSlot(@PathVariable Long teacherId, @PathVariable Long slotId, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        teacherWorkService.archiveSlot(teacherId, slotId, principal.getId(), isSuperAdmin(principal));
    }

    @PutMapping("/teachers/{teacherId}/sessions")
    public TeacherWorkDto.SessionInfo recordSession(@PathVariable Long teacherId,
                                                    @Valid @RequestBody TeacherSessionRequest request,
                                                    Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.recordSession(teacherId, request, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/teachers/{teacherId}/extras")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherWorkDto.ExtraInfo addExtra(@PathVariable Long teacherId, @Valid @RequestBody TeacherExtraRequest request,
                                             Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.addExtra(teacherId, request, principal.getId(), isSuperAdmin(principal));
    }

    @DeleteMapping("/teachers/{teacherId}/extras/{extraId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExtra(@PathVariable Long teacherId, @PathVariable Long extraId, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        teacherWorkService.deleteExtra(teacherId, extraId, principal.getId(), isSuperAdmin(principal));
    }

    @PostMapping("/teachers/{teacherId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherWorkDto.PaymentInfo addPayment(@PathVariable Long teacherId,
                                                 @Valid @RequestBody TeacherPaymentRequest request,
                                                 Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return teacherWorkService.addPayment(teacherId, request, principal.getId(), isSuperAdmin(principal));
    }

    @DeleteMapping("/teachers/{teacherId}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long teacherId, @PathVariable Long paymentId, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        teacherWorkService.deletePayment(teacherId, paymentId, principal.getId(), isSuperAdmin(principal));
    }

    private static boolean isSuperAdmin(UserPrincipal principal) {
        return principal.getRoles().contains("SUPER_ADMIN");
    }

    private static UserPrincipal requireOwner(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        if (!principal.getRoles().contains("SCHOOL_ADMIN") && !principal.getRoles().contains("STAFF")
                && !principal.getRoles().contains("SUPER_ADMIN")) {
            throw new AccessDeniedException("Accès réservé au propriétaire de l'établissement");
        }
        return principal;
    }
}