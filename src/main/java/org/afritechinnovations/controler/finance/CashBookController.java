package org.afritechinnovations.controler.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.CashBookDto.*;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.finance.CashBookService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/cash-book/schools/{schoolId}")
public class CashBookController {
    private final CashBookService service;
    @GetMapping public Overview overview(@PathVariable Long schoolId,@RequestParam String month,Authentication auth) {
        var p=principal(auth); return service.overview(schoolId,month,p.getId(),admin(p));
    }
    @PostMapping("/open") public void open(@PathVariable Long schoolId,@Valid @RequestBody Open request,Authentication auth) {
        var p=principal(auth); service.open(schoolId,request,p.getId(),admin(p));
    }
    @PostMapping("/entries") public void add(@PathVariable Long schoolId,@Valid @RequestBody Entry request,Authentication auth) {
        var p=principal(auth); service.add(schoolId,request,p.getId(),admin(p));
    }
    @DeleteMapping("/entries/{id}") public void delete(@PathVariable Long schoolId,@PathVariable Long id,Authentication auth) {
        var p=principal(auth); service.delete(schoolId,id,p.getId(),admin(p));
    }
    @PostMapping("/close") public void close(@PathVariable Long schoolId,@Valid @RequestBody Close request,Authentication auth) {
        var p=principal(auth); service.close(schoolId,request,p.getId(),admin(p));
    }
    private static boolean admin(UserPrincipal p) { return p.getRoles().contains("SUPER_ADMIN"); }
    private static UserPrincipal principal(Authentication auth) {
        if(auth==null || !(auth.getPrincipal() instanceof UserPrincipal p)
                || p.getRoles().stream().noneMatch(r->r.equals("SCHOOL_ADMIN") || r.equals("STAFF") || r.equals("SUPER_ADMIN")))
            throw new AccessDeniedException("Authentification requise pour accéder à la caisse");
        return p;
    }
}
