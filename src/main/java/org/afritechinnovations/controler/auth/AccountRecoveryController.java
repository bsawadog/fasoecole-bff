package org.afritechinnovations.controler.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.auth.AccountRecoveryDto.*;
import org.afritechinnovations.service.auth.AccountRecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController @RequiredArgsConstructor
public class AccountRecoveryController {
    private final AccountRecoveryService service;
    @PostMapping("/api/auth/account-recovery") @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId="submitAccountRecovery")
    public Map<String,String> submit(@Valid @RequestBody Submit request) {
        service.submit(request);
        return Map.of("message", "Votre demande a été reçue. Si les informations correspondent à un dossier, l’établissement ou le support vérifiera votre identité avant de vous envoyer un lien. Aucun identifiant n’a été modifié. Contactez votre école pour le suivi.");
    }
    @PostMapping("/api/auth/account-recovery/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId="completeAccountRecovery")
    public void complete(@Valid @RequestBody Complete request) { service.complete(request); }
    @GetMapping("/api/account-recovery")
    @Operation(operationId="listAccountRecoveryRequests")
    public List<View> list(@RequestParam(required=false) Long schoolId) { return service.list(schoolId); }
    @PostMapping("/api/account-recovery/{id}/decision")
    @Operation(operationId="decideAccountRecovery")
    public View decide(@PathVariable Long id, @Valid @RequestBody Decision request) { return service.decide(id,request); }
    @PostMapping("/api/account-recovery/{id}/resend")
    @Operation(operationId="resendAccountRecovery")
    public View resend(@PathVariable Long id) { return service.resend(id); }
}
