package org.afritechinnovations.controler.people;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.CreateStudentInvoiceRequest;
import org.afritechinnovations.dto.people.CreateStudentPaymentRequest;
import org.afritechinnovations.dto.people.StudentDetailDto;
import org.afritechinnovations.dto.people.UpsertAttendanceRequest;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.people.ClassRosterService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentDetailController {

    private final ClassRosterService classRosterService;

    @GetMapping("/{studentId}/detail")
    public StudentDetailDto getDetail(@PathVariable Long studentId, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.getStudentDetail(studentId, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PostMapping("/{studentId}/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDetailDto.AttendanceInfo addAttendance(@PathVariable Long studentId,
                                                          @Valid @RequestBody UpsertAttendanceRequest request,
                                                          Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.addAttendance(studentId, request, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PutMapping("/{studentId}/attendance/{attendanceId}")
    public StudentDetailDto.AttendanceInfo updateAttendance(@PathVariable Long studentId,
                                                             @PathVariable Long attendanceId,
                                                             @Valid @RequestBody UpsertAttendanceRequest request,
                                                             Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.updateAttendance(studentId, attendanceId, request, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @DeleteMapping("/{studentId}/attendance/{attendanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttendance(@PathVariable Long studentId, @PathVariable Long attendanceId,
                                  Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        classRosterService.deleteAttendance(studentId, attendanceId, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PostMapping("/{studentId}/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDetailDto.InvoiceInfo createInvoice(@PathVariable Long studentId,
                                                       @Valid @RequestBody CreateStudentInvoiceRequest request,
                                                       Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.createInvoice(studentId, request, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PostMapping("/{studentId}/invoices/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDetailDto.InvoiceInfo addPayment(@PathVariable Long studentId,
                                                    @PathVariable Long invoiceId,
                                                    @Valid @RequestBody CreateStudentPaymentRequest request,
                                                    Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.addPayment(studentId, invoiceId, request, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PutMapping("/{studentId}/invoices/{invoiceId}/payments/{paymentId}")
    public StudentDetailDto.InvoiceInfo updatePayment(@PathVariable Long studentId, @PathVariable Long invoiceId,
                                                       @PathVariable Long paymentId,
                                                       @Valid @RequestBody CreateStudentPaymentRequest request,
                                                       Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.updatePayment(studentId, invoiceId, paymentId, request, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @DeleteMapping("/{studentId}/invoices/{invoiceId}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long studentId, @PathVariable Long invoiceId, @PathVariable Long paymentId,
                              Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        classRosterService.deletePayment(studentId, invoiceId, paymentId, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PutMapping("/{studentId}/invoices/{invoiceId}/cancel")
    public StudentDetailDto.InvoiceInfo cancelInvoice(@PathVariable Long studentId, @PathVariable Long invoiceId,
                               Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.cancelInvoice(studentId, invoiceId, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    private UserPrincipal requireOwner(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        if (!principal.getRoles().contains("SCHOOL_ADMIN") && !principal.getRoles().contains("SUPER_ADMIN")) {
            throw new AccessDeniedException("Accès réservé au propriétaire de l'établissement");
        }
        return principal;
    }
}
