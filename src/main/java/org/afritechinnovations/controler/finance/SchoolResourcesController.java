package org.afritechinnovations.controler.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.SchoolResourcesDto.*;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.finance.SchoolResourcesService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/resources/schools/{schoolId}")
public class SchoolResourcesController {
    private final SchoolResourcesService service;
    @GetMapping("/assets") public AssetsOverview assets(@PathVariable Long schoolId, Authentication auth) {
        var p = principal(auth); return service.assets(schoolId, p.getId(), admin(p));
    }
    @PostMapping("/assets") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Asset createAsset(@PathVariable Long schoolId, @Valid @RequestBody AssetInput input, Authentication auth) {
        var p = principal(auth); return service.saveAsset(schoolId, null, input, p.getId(), admin(p));
    }
    @PutMapping("/assets/{id}") public Asset updateAsset(@PathVariable Long schoolId, @PathVariable Long id, @Valid @RequestBody AssetInput input, Authentication auth) {
        var p = principal(auth); return service.saveAsset(schoolId, id, input, p.getId(), admin(p));
    }
    @GetMapping("/assets/{id}/history") public List<AssetEvent> assetHistory(@PathVariable Long schoolId, @PathVariable Long id, Authentication auth) {
        var p = principal(auth); return service.assetHistory(schoolId, id, p.getId(), admin(p));
    }
    @GetMapping("/stock") public StockOverview stock(@PathVariable Long schoolId, Authentication auth) {
        var p = principal(auth); return service.stock(schoolId, p.getId(), admin(p));
    }
    @PostMapping("/stock") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public StockItem createStock(@PathVariable Long schoolId, @Valid @RequestBody StockInput input, Authentication auth) {
        var p = principal(auth); return service.saveStockItem(schoolId, null, input, p.getId(), admin(p));
    }
    @PutMapping("/stock/{id}") public StockItem updateStock(@PathVariable Long schoolId, @PathVariable Long id, @Valid @RequestBody StockInput input, Authentication auth) {
        var p = principal(auth); return service.saveStockItem(schoolId, id, input, p.getId(), admin(p));
    }
    @PostMapping("/stock/{id}/movements") public StockItem move(@PathVariable Long schoolId, @PathVariable Long id, @Valid @RequestBody MovementInput input, Authentication auth) {
        var p = principal(auth); return service.move(schoolId, id, input, p.getId(), admin(p));
    }
    @GetMapping("/stock/{id}/movements") public List<StockMovement> movements(@PathVariable Long schoolId, @PathVariable Long id, Authentication auth) {
        var p = principal(auth); return service.movements(schoolId, id, p.getId(), admin(p));
    }
    private static boolean admin(UserPrincipal p) { return p.getRoles().contains("SUPER_ADMIN"); }
    private static UserPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)
                || p.getRoles().stream().noneMatch(role -> role.equals("SCHOOL_ADMIN") || role.equals("STAFF") || role.equals("SUPER_ADMIN")))
            throw new AccessDeniedException("Authentification requise.");
        return p;
    }
}
