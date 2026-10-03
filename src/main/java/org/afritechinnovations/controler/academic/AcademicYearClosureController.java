package org.afritechinnovations.controler.academic;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.academic.AcademicYearClosureService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/enrollment/schools/{schoolId}")
public class AcademicYearClosureController {
    private final AcademicYearClosureService service;
    @PostMapping("/close")
    public AcademicYearClosureService.CloseResult close(@PathVariable Long schoolId,
            @Valid @RequestBody AcademicYearClosureService.CloseRequest request, Authentication authentication) {
        var p = principal(authentication);
        return service.close(schoolId,request,p.getId(),p.getRoles().contains("SUPER_ADMIN"));
    }
    @GetMapping("/years/{yearId}/balances")
    public Map<String,Object> balances(@PathVariable Long schoolId,@PathVariable Long yearId,Authentication authentication) {
        var p = principal(authentication);
        return service.balances(schoolId,yearId,p.getId(),p.getRoles().contains("SUPER_ADMIN"));
    }
    private UserPrincipal principal(Authentication authentication) {
        if(authentication==null || !(authentication.getPrincipal() instanceof UserPrincipal p))
            throw new AccessDeniedException("Authentification requise.");
        return p;
    }
}
