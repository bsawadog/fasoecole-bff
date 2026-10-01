package org.afritechinnovations.controler.finance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.OwnerExpenseDto;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.finance.OwnerExpenseService;
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
@RequestMapping("/api/owner/expenses")
@RequiredArgsConstructor
public class OwnerExpenseController {

    private final OwnerExpenseService expenseService;

    @GetMapping("/schools/{schoolId}/summary")
    public OwnerExpenseDto.Summary summary(@PathVariable Long schoolId,
                                           @RequestParam(required = false) Long academicYearId,
                                           Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return expenseService.summary(schoolId, academicYearId, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/schools/{schoolId}/budget")
    public OwnerExpenseDto.Summary saveBudget(@PathVariable Long schoolId,
                                              @Valid @RequestBody OwnerExpenseDto.BudgetRequest request,
                                              Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return expenseService.saveBudget(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/categories")
    public List<OwnerExpenseDto.CategoryInfo> categories(@PathVariable Long schoolId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return expenseService.listCategories(schoolId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/schools/{schoolId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerExpenseDto.CategoryInfo createCategory(@PathVariable Long schoolId,
                                                       @Valid @RequestBody OwnerExpenseDto.CategoryRequest request,
                                                       Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return expenseService.createCategory(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/categories/{categoryId}")
    public OwnerExpenseDto.CategoryInfo updateCategory(@PathVariable Long categoryId,
                                                       @Valid @RequestBody OwnerExpenseDto.CategoryRequest request,
                                                       Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return expenseService.updateCategory(categoryId, request, p.getId(), isSuperAdmin(p));
    }

    @DeleteMapping("/categories/{categoryId}")
    public Map<String, Boolean> deleteCategory(@PathVariable Long categoryId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return Map.of("deleted", expenseService.deleteCategory(categoryId, p.getId(), isSuperAdmin(p)));
    }

    @GetMapping("/schools/{schoolId}/expenses")
    public List<OwnerExpenseDto.ExpenseRow> expenses(@PathVariable Long schoolId,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) Long categoryId,
                                                     Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return expenseService.listExpenses(schoolId, from, to, categoryId, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/expenses/export")
    public ResponseEntity<byte[]> export(@PathVariable Long schoolId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Long categoryId,
                                         Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        byte[] body = expenseService.exportExpensesCsv(schoolId, from, to, categoryId, p.getId(), isSuperAdmin(p))
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"depenses-ecole-" + schoolId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping("/schools/{schoolId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerExpenseDto.ExpenseRow createExpense(@PathVariable Long schoolId,
                                                    @Valid @RequestBody OwnerExpenseDto.ExpenseRequest request,
                                                    Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return expenseService.createExpense(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/expenses/{expenseId}")
    public OwnerExpenseDto.ExpenseRow updateExpense(@PathVariable Long expenseId,
                                                    @Valid @RequestBody OwnerExpenseDto.ExpenseRequest request,
                                                    Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return expenseService.updateExpense(expenseId, request, p.getId(), isSuperAdmin(p));
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@PathVariable Long expenseId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        expenseService.deleteExpense(expenseId, p.getId(), isSuperAdmin(p));
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
