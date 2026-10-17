package org.afritechinnovations.controler.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PayrollDto.*;
import org.afritechinnovations.dto.finance.PayableDto;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.finance.PayrollService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/payroll/schools/{schoolId}")
public class PayrollController {
    private final PayrollService service;
    @PostMapping("/teachers/{teacherId}/import")
    public void importTeacherSalary(@PathVariable Long schoolId, @PathVariable Long teacherId,
            @Valid @RequestBody org.afritechinnovations.dto.finance.ImportTeacherSalaryRequest request, Authentication auth) {
        var p = principal(auth); service.importTeacherSalary(schoolId, teacherId, request, p.getId(), admin(p));
    }
    @GetMapping public Overview overview(@PathVariable Long schoolId,@RequestParam String month,Authentication auth) {
        var p = principal(auth); return service.overview(schoolId,month,p.getId(),admin(p));
    }
    @PostMapping("/prepare") public Overview prepare(@PathVariable Long schoolId,@RequestParam String month,Authentication auth) {
        var p = principal(auth); return service.prepare(schoolId,month,p.getId(),admin(p));
    }
    @GetMapping("/employees/{userId}") public EmployeeDetail detail(@PathVariable Long schoolId,@PathVariable Long userId,@RequestParam String month,Authentication auth) {
        var p = principal(auth); return service.detail(schoolId,userId,month,p.getId(),admin(p));
    }
    @PostMapping("/payments/{id}") public PayableDto.Row pay(@PathVariable Long schoolId,@PathVariable Long id,@Valid @RequestBody PayableDto.Pay request,Authentication auth) {
        var p = principal(auth); return service.pay(schoolId,id,request,p.getId(),admin(p));
    }
    @PutMapping("/payments/{id}/taxes") public void taxes(@PathVariable Long schoolId,@PathVariable Long id,@Valid @RequestBody TaxInput request,Authentication auth) {
        var p = principal(auth); service.taxes(schoolId,id,request,p.getId(),admin(p));
    }
    @PostMapping("/documents") public Document issue(@PathVariable Long schoolId,@Valid @RequestBody DocumentInput request,Authentication auth) {
        var p = principal(auth); return service.issue(schoolId,request,p.getId(),admin(p));
    }
    @GetMapping("/documents/{id}") public Document document(@PathVariable Long schoolId,@PathVariable Long id,Authentication auth) {
        var p = principal(auth); return service.document(schoolId,id,p.getId(),admin(p));
    }
    private static UserPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)
                || p.getRoles().stream().noneMatch(r -> r.equals("SCHOOL_ADMIN") || r.equals("STAFF") || r.equals("SUPER_ADMIN")))
            throw new AccessDeniedException("Authentification requise pour accéder aux salaires.");
        return p;
    }
    private static boolean admin(UserPrincipal p) { return p.getRoles().contains("SUPER_ADMIN"); }
}
