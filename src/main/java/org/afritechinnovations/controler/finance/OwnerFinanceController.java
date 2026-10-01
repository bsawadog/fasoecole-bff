package org.afritechinnovations.controler.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.OwnerFinanceDto;
import org.afritechinnovations.dto.people.CreateStudentPaymentRequest;
import org.afritechinnovations.dto.people.StudentDetailDto;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.finance.OwnerFinanceService;
import org.afritechinnovations.service.people.ClassRosterService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/finance")
@RequiredArgsConstructor
public class OwnerFinanceController {

    private final OwnerFinanceService financeService;
    private final ClassRosterService classRosterService;

    @GetMapping("/schools/{schoolId}/overview")
    public OwnerFinanceDto.Overview overview(@PathVariable Long schoolId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.overview(schoolId, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/fee-types")
    public List<OwnerFinanceDto.FeeTypeInfo> feeTypes(@PathVariable Long schoolId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.listFeeTypes(schoolId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/schools/{schoolId}/fee-types")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerFinanceDto.FeeTypeInfo createFeeType(@PathVariable Long schoolId,
                                                     @Valid @RequestBody OwnerFinanceDto.FeeTypeRequest request,
                                                     Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.createFeeType(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/fee-types/{feeTypeId}")
    public OwnerFinanceDto.FeeTypeInfo updateFeeType(@PathVariable Long feeTypeId,
                                                     @Valid @RequestBody OwnerFinanceDto.FeeTypeRequest request,
                                                     Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.updateFeeType(feeTypeId, request, p.getId(), isSuperAdmin(p));
    }

    @DeleteMapping("/fee-types/{feeTypeId}")
    public Map<String, Boolean> deleteFeeType(@PathVariable Long feeTypeId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return Map.of("deleted", financeService.deleteFeeType(feeTypeId, p.getId(), isSuperAdmin(p)));
    }

    @PostMapping("/schools/{schoolId}/invoices/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerFinanceDto.BulkInvoiceResult bulkInvoice(@PathVariable Long schoolId,
                                                         @Valid @RequestBody OwnerFinanceDto.BulkInvoiceRequest request,
                                                         Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.bulkInvoice(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/invoices")
    public List<OwnerFinanceDto.InvoiceRow> invoices(@PathVariable Long schoolId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long classId,
                                                     Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.listInvoices(schoolId, status, classId, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/payments")
    public List<OwnerFinanceDto.PaymentRow> payments(@PathVariable Long schoolId,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.listPayments(schoolId, from, to, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/payments/export")
    public ResponseEntity<byte[]> exportPayments(@PathVariable Long schoolId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        byte[] body = financeService.exportPaymentsCsv(schoolId, from, to, p.getId(), isSuperAdmin(p))
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"paiements-ecole-" + schoolId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDetailDto.InvoiceInfo recordPayment(@PathVariable Long invoiceId,
                                                      @Valid @RequestBody CreateStudentPaymentRequest request,
                                                      Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        Long studentId = financeService.studentIdOf(invoiceId, p.getId(), isSuperAdmin(p));
        return classRosterService.addPayment(studentId, invoiceId, request, p.getId(), isSuperAdmin(p));
    }

    @DeleteMapping("/invoices/{invoiceId}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long invoiceId, @PathVariable Long paymentId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        Long studentId = financeService.studentIdOf(invoiceId, p.getId(), isSuperAdmin(p));
        classRosterService.deletePayment(studentId, invoiceId, paymentId, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/invoices/{invoiceId}/discount")
    public OwnerFinanceDto.InvoiceRow discount(@PathVariable Long invoiceId,
                                               @Valid @RequestBody OwnerFinanceDto.DiscountRequest request,
                                               Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.applyDiscount(invoiceId, request, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/invoices/{invoiceId}/cancel")
    public StudentDetailDto.InvoiceInfo cancel(@PathVariable Long invoiceId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        Long studentId = financeService.studentIdOf(invoiceId, p.getId(), isSuperAdmin(p));
        return classRosterService.cancelInvoice(studentId, invoiceId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/invoices/{invoiceId}/reminder")
    public OwnerFinanceDto.ReminderResult reminder(@PathVariable Long invoiceId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return financeService.sendReminder(invoiceId, p.getId(), isSuperAdmin(p));
    }

    private static boolean isSuperAdmin(UserPrincipal principal) {
        return principal.getRoles().contains("SUPER_ADMIN");
    }

    private static UserPrincipal requireOwner(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        if (!principal.getRoles().contains("SCHOOL_ADMIN") && !principal.getRoles().contains("SUPER_ADMIN")) {
            throw new AccessDeniedException("Accès réservé au propriétaire de l'établissement");
        }
        return principal;
    }
}
