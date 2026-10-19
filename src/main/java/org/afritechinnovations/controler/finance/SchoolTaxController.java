package org.afritechinnovations.controler.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PayableDto;
import org.afritechinnovations.dto.finance.SchoolTaxDto.*;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.finance.SchoolTaxService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/taxes/schools/{schoolId}")
public class SchoolTaxController {
    private final SchoolTaxService service;
    @PostMapping("/synchronize-payroll") public Overview synchronizePayroll(@PathVariable Long schoolId,@RequestParam int year,Authentication auth){var p=principal(auth);return service.synchronizePayroll(schoolId,year,p.getId(),admin(p));}
    @GetMapping public Overview overview(@PathVariable Long schoolId,@RequestParam int year,Authentication auth){var p=principal(auth);return service.overview(schoolId,year,p.getId(),admin(p));}
    @GetMapping("/{id}") public Detail detail(@PathVariable Long schoolId,@PathVariable Long id,Authentication auth){var p=principal(auth);return service.detail(schoolId,id,p.getId(),admin(p));}
    @PostMapping public Row create(@PathVariable Long schoolId,@Valid @RequestBody Create input,Authentication auth){var p=principal(auth);return service.create(schoolId,input,p.getId(),admin(p));}
    @PutMapping("/{id}") public Row edit(@PathVariable Long schoolId,@PathVariable Long id,@Valid @RequestBody Edit input,Authentication auth){var p=principal(auth);return service.edit(schoolId,id,input,p.getId(),admin(p));}
    @PostMapping("/{id}/cancel") public void cancel(@PathVariable Long schoolId,@PathVariable Long id,@Valid @RequestBody Cancel input,Authentication auth){var p=principal(auth);service.cancel(schoolId,id,input,p.getId(),admin(p));}
    @PostMapping("/{id}/payments") public PayableDto.Row pay(@PathVariable Long schoolId,@PathVariable Long id,@Valid @RequestBody PayableDto.Pay input,Authentication auth){var p=principal(auth);return service.pay(schoolId,id,input,p.getId(),admin(p));}
    @GetMapping("/payroll-preview") public PayrollPreview preview(@PathVariable Long schoolId,@RequestParam String month,Authentication auth){var p=principal(auth);return service.preview(schoolId,month,p.getId(),admin(p));}
    @PostMapping("/payroll-import") public void importPayroll(@PathVariable Long schoolId,@Valid @RequestBody ImportPayroll input,Authentication auth){var p=principal(auth);service.importPayroll(schoolId,input,p.getId(),admin(p));}
    private static UserPrincipal principal(Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof UserPrincipal p)||p.getRoles().stream().noneMatch(r->r.equals("SCHOOL_ADMIN")||r.equals("STAFF")||r.equals("SUPER_ADMIN")))throw new AccessDeniedException("Authentification requise.");return p;
    }
    private static boolean admin(UserPrincipal p){return p.getRoles().contains("SUPER_ADMIN");}
}
