package org.afritechinnovations.controler.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PayableDto;
import org.afritechinnovations.dto.finance.OwnerExpenseDto;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.finance.SchoolPayableService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/payables/schools/{schoolId}")
public class SchoolPayableController {
    private final SchoolPayableService service;
    @GetMapping public PayableDto.Overview overview(@PathVariable Long schoolId,@RequestParam String month,
            @RequestParam(defaultValue="false") boolean salariesOnly,Authentication auth) {
        var p=principal(auth); return service.overview(schoolId,month,salariesOnly,p.getId(),admin(p));
    }
    @GetMapping("/categories") public List<OwnerExpenseDto.CategoryInfo> categories(@PathVariable Long schoolId,Authentication auth) {
        var p=principal(auth); return service.categories(schoolId,p.getId(),admin(p));
    }
    @PostMapping("/prepare") public void prepare(@PathVariable Long schoolId,@RequestParam String month,Authentication auth) {
        var p=principal(auth); service.prepare(schoolId,month,p.getId(),admin(p));
    }
    @PostMapping public void create(@PathVariable Long schoolId,@Valid @RequestBody PayableDto.Create request,Authentication auth) {
        var p=principal(auth); service.create(schoolId,request,p.getId(),admin(p));
    }
    @PostMapping("/fixed") public void fixed(@PathVariable Long schoolId,@Valid @RequestBody PayableDto.FixedRequest request,Authentication auth) {
        var p=principal(auth); service.createFixed(schoolId,request,p.getId(),admin(p));
    }
    @PutMapping("/fixed/{id}") public void active(@PathVariable Long schoolId,@PathVariable Long id,@RequestParam boolean active,Authentication auth) {
        var p=principal(auth); service.setFixedActive(schoolId,id,active,p.getId(),admin(p));
    }
    @GetMapping("/{id}/payments") public List<PayableDto.Payment> payments(@PathVariable Long schoolId,@PathVariable Long id,Authentication auth) {
        var p=principal(auth); return service.payments(schoolId,id,p.getId(),admin(p));
    }
    @PostMapping("/{id}/payments") public PayableDto.Row pay(@PathVariable Long schoolId,@PathVariable Long id,
            @Valid @RequestBody PayableDto.Pay request,Authentication auth) {
        var p=principal(auth); return service.pay(schoolId,id,request,p.getId(),admin(p));
    }
    @PostMapping("/{id}/cancel") public void cancel(@PathVariable Long schoolId,@PathVariable Long id,Authentication auth) {
        var p=principal(auth); service.cancel(schoolId,id,p.getId(),admin(p));
    }
    private static UserPrincipal principal(Authentication auth) {
        if(auth==null || !(auth.getPrincipal() instanceof UserPrincipal p)) throw new AccessDeniedException("Authentification requise");
        if(!p.getRoles().contains("SCHOOL_ADMIN") && !p.getRoles().contains("STAFF") && !p.getRoles().contains("SUPER_ADMIN"))
            throw new AccessDeniedException("Accès aux finances réservé à la direction");
        return p;
    }
    private static boolean admin(UserPrincipal p) { return p.getRoles().contains("SUPER_ADMIN"); }
}
