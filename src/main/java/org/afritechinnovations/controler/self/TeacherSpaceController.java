package org.afritechinnovations.controler.self;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.TeacherSpaceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Espace enseignant : uniquement les classes et matières affectées à l'utilisateur connecté. */
@RestController
@RequestMapping("/api/me/teacher")
@RequiredArgsConstructor
public class TeacherSpaceController {

    private final TeacherSpaceService service;
    private final AccessGuard guard;

    @GetMapping("/classes")
    public List<SelfServiceDto.TeacherClass> classes() {
        return service.classes(guard.currentUserId());
    }

    @GetMapping("/classes/{classId}/students")
    public List<SelfServiceDto.RosterStudent> students(@PathVariable Long classId) {
        return service.students(guard.currentUserId(), classId);
    }

    @GetMapping("/schedule")
    public List<SelfServiceDto.ScheduleEntry> schedule() {
        return service.schedule(guard.currentUserId());
    }

    @GetMapping("/classes/{classId}/periods")
    public List<OwnerGradeDto.PeriodInfo> periods(@PathVariable Long classId) {
        return service.periods(guard.currentUserId(), classId);
    }

    @GetMapping("/classes/{classId}/evaluations")
    public List<OwnerGradeDto.EvaluationInfo> evaluations(@PathVariable Long classId, @RequestParam Long periodId) {
        return service.evaluations(guard.currentUserId(), classId, periodId);
    }

    @PostMapping("/classes/{classId}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerGradeDto.EvaluationInfo createEvaluation(@PathVariable Long classId,
                                                         @Valid @RequestBody OwnerGradeDto.EvaluationRequest request) {
        return service.createEvaluation(guard.currentUserId(), classId, request);
    }

    @PutMapping("/evaluations/{evaluationId}")
    public OwnerGradeDto.EvaluationInfo updateEvaluation(@PathVariable Long evaluationId,
                                                         @Valid @RequestBody OwnerGradeDto.EvaluationRequest request) {
        return service.updateEvaluation(guard.currentUserId(), evaluationId, request);
    }

    @DeleteMapping("/evaluations/{evaluationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvaluation(@PathVariable Long evaluationId, @RequestParam(required = false) String reason) {
        service.deleteEvaluation(guard.currentUserId(), evaluationId, reason);
    }

    @GetMapping("/evaluations/{evaluationId}/sheet")
    public OwnerGradeDto.GradeSheet gradeSheet(@PathVariable Long evaluationId) {
        return service.gradeSheet(guard.currentUserId(), evaluationId);
    }

    @PutMapping("/evaluations/{evaluationId}/grades")
    public OwnerGradeDto.SaveGradesResult saveGrades(@PathVariable Long evaluationId,
                                                     @Valid @RequestBody OwnerGradeDto.SaveGradesRequest request) {
        return service.saveGrades(guard.currentUserId(), evaluationId, request);
    }
}
