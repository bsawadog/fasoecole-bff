package org.afritechinnovations.controler.self;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.TeacherSpaceService;
import org.afritechinnovations.service.communication.SchoolInboxService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

/** Espace enseignant : uniquement les classes et matières affectées à l'utilisateur connecté. */
@RestController
@RequestMapping("/api/me/teacher")
@RequiredArgsConstructor
public class TeacherSpaceController {

    private final TeacherSpaceService service;
    private final SchoolInboxService inboxService;
    private final AccessGuard guard;

    @GetMapping("/classes")
    public List<SelfServiceDto.TeacherClass> classes() {
        return service.classes(guard.currentUserId());
    }

    @GetMapping("/classes/{classId}/students")
    public List<SelfServiceDto.RosterStudent> students(@PathVariable Long classId) {
        return service.students(guard.currentUserId(), classId);
    }

    @GetMapping("/classes/{classId}/family-reports")
    public List<FamilyContactDto.AbsenceReportItem> familyReports(
            @PathVariable Long classId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.familyReports(guard.currentUserId(), classId, date);
    }

    @PostMapping("/classes/{classId}/family-reports/{reportId}/record-attendance")
    public FamilyContactDto.AbsenceReportItem recordFamilyReport(@PathVariable Long classId,
                                                                 @PathVariable Long reportId) {
        return service.recordFamilyReport(guard.currentUserId(), classId, reportId);
    }

    @GetMapping("/classes/{classId}/conversations")
    public List<FamilyContactDto.ConversationSummary> conversations(@PathVariable Long classId) {
        return inboxService.teacherConversations(guard.currentUserId(), classId);
    }

    @GetMapping("/classes/{classId}/conversations/{conversationId}")
    public FamilyContactDto.ConversationThread conversation(@PathVariable Long classId,
                                                             @PathVariable Long conversationId) {
        return inboxService.teacherThread(guard.currentUserId(), classId, conversationId);
    }

    @PostMapping("/classes/{classId}/conversations/{conversationId}/messages")
    public FamilyContactDto.ConversationThread reply(@PathVariable Long classId,
                                                     @PathVariable Long conversationId,
                                                     @Valid @RequestBody FamilyContactDto.ReplyRequest request) {
        return inboxService.teacherReply(guard.currentUserId(), classId, conversationId, request);
    }

    @GetMapping("/schedule")
    public List<SelfServiceDto.ScheduleEntry> schedule() {
        return service.schedule(guard.currentUserId());
    }

    @GetMapping("/classes/{classId}/attendance")
    public List<org.afritechinnovations.dto.self.TeacherAttendanceDto.Item> attendance(@PathVariable Long classId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.attendance(guard.currentUserId(),classId,date);
    }

    @PutMapping("/classes/{classId}/students/{studentId}/attendance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveAttendance(@PathVariable Long classId,@PathVariable Long studentId,
            @Valid @RequestBody org.afritechinnovations.dto.self.TeacherAttendanceDto.Request request) {
        service.saveAttendance(guard.currentUserId(),classId,studentId,request);
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
