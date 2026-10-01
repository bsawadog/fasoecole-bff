package org.afritechinnovations.controler.academic;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.academic.OwnerGradeService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Notes & bulletins — réservé au propriétaire de l'établissement (ou super admin). */
@RestController
@RequestMapping("/api/owner/grades")
@RequiredArgsConstructor
public class OwnerGradeController {

    private final OwnerGradeService gradeService;

    // ------------------------------------------------------------------ périodes

    @GetMapping("/schools/{schoolId}/periods")
    public List<OwnerGradeDto.PeriodInfo> periods(@PathVariable Long schoolId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.listPeriods(schoolId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/schools/{schoolId}/periods")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerGradeDto.PeriodInfo createPeriod(@PathVariable Long schoolId,
                                                 @Valid @RequestBody OwnerGradeDto.PeriodRequest request,
                                                 Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.createPeriod(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/schools/{schoolId}/periods/defaults")
    @ResponseStatus(HttpStatus.CREATED)
    public List<OwnerGradeDto.PeriodInfo> createDefaultPeriods(@PathVariable Long schoolId,
                                                               @Valid @RequestBody OwnerGradeDto.DefaultPeriodsRequest request,
                                                               Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.createDefaultPeriods(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/periods/{periodId}")
    public OwnerGradeDto.PeriodInfo updatePeriod(@PathVariable Long periodId,
                                                 @Valid @RequestBody OwnerGradeDto.PeriodRequest request,
                                                 Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.updatePeriod(periodId, request, p.getId(), isSuperAdmin(p));
    }

    @DeleteMapping("/periods/{periodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePeriod(@PathVariable Long periodId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        gradeService.deletePeriod(periodId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/periods/{periodId}/status")
    public OwnerGradeDto.PeriodInfo changeStatus(@PathVariable Long periodId,
                                                 @Valid @RequestBody OwnerGradeDto.StatusRequest request,
                                                 Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.changeStatus(periodId, request.status(), p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/periods/{periodId}/summary")
    public OwnerGradeDto.SchoolSummary summary(@PathVariable Long periodId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.schoolSummary(periodId, p.getId(), isSuperAdmin(p));
    }

    // ------------------------------------------------------------------ matières

    @GetMapping("/classes/{classId}/subjects")
    public List<OwnerGradeDto.ClassSubjectInfo> subjects(@PathVariable Long classId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.listClassSubjects(classId, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/classes/{classId}/subjects/{subjectId}/coefficient")
    public List<OwnerGradeDto.ClassSubjectInfo> coefficient(@PathVariable Long classId, @PathVariable Long subjectId,
                                                            @Valid @RequestBody OwnerGradeDto.CoefficientRequest request,
                                                            Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.updateCoefficient(classId, subjectId, request.coefficient(), p.getId(), isSuperAdmin(p));
    }

    // ------------------------------------------------------------------ évaluations & saisie

    @GetMapping("/classes/{classId}/evaluations")
    public List<OwnerGradeDto.EvaluationInfo> evaluations(@PathVariable Long classId, @RequestParam Long periodId,
                                                          Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.listEvaluations(classId, periodId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/classes/{classId}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerGradeDto.EvaluationInfo createEvaluation(@PathVariable Long classId,
                                                         @Valid @RequestBody OwnerGradeDto.EvaluationRequest request,
                                                         Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.createEvaluation(classId, request, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/evaluations/{evaluationId}")
    public OwnerGradeDto.EvaluationInfo updateEvaluation(@PathVariable Long evaluationId,
                                                         @Valid @RequestBody OwnerGradeDto.EvaluationRequest request,
                                                         Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.updateEvaluation(evaluationId, request, p.getId(), isSuperAdmin(p));
    }

    @DeleteMapping("/evaluations/{evaluationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvaluation(@PathVariable Long evaluationId, @RequestParam(required = false) String reason,
                                 Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        gradeService.deleteEvaluation(evaluationId, reason, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/evaluations/{evaluationId}/sheet")
    public OwnerGradeDto.GradeSheet sheet(@PathVariable Long evaluationId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.gradeSheet(evaluationId, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/evaluations/{evaluationId}/grades")
    public OwnerGradeDto.SaveGradesResult saveGrades(@PathVariable Long evaluationId,
                                                     @Valid @RequestBody OwnerGradeDto.SaveGradesRequest request,
                                                     Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.saveGrades(evaluationId, request, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/classes/{classId}/history")
    public List<OwnerGradeDto.HistoryEntry> history(@PathVariable Long classId, @RequestParam Long periodId,
                                                    Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.history(classId, periodId, p.getId(), isSuperAdmin(p));
    }

    // ------------------------------------------------------------------ résultats & bulletins

    @GetMapping("/classes/{classId}/results")
    public OwnerGradeDto.ClassResults results(@PathVariable Long classId, @RequestParam Long periodId,
                                              Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.classResults(classId, periodId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/classes/{classId}/report-cards")
    public OwnerGradeDto.ClassResults generateReportCards(@PathVariable Long classId, @RequestParam Long periodId,
                                                          Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.generateReportCards(classId, periodId, p.getId(), isSuperAdmin(p));
    }

    @PutMapping("/classes/{classId}/report-cards/{studentId}/comment")
    public OwnerGradeDto.ClassResults comment(@PathVariable Long classId, @PathVariable Long studentId,
                                              @RequestParam Long periodId,
                                              @Valid @RequestBody OwnerGradeDto.CommentRequest request,
                                              Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.saveComment(classId, periodId, studentId, request.comment(), p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/classes/{classId}/bulletins")
    public OwnerGradeDto.BulletinBatch bulletins(@PathVariable Long classId, @RequestParam Long periodId,
                                                 @RequestParam(required = false) Long studentId,
                                                 Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return gradeService.bulletins(classId, periodId, studentId, p.getId(), isSuperAdmin(p));
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
