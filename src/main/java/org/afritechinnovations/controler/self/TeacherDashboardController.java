package org.afritechinnovations.controler.self;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.TeacherDashboardService;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/me/teacher/dashboard")
public class TeacherDashboardController {
 private final AccessGuard guard;private final TeacherDashboardService service;
 @GetMapping public TeacherDashboardService.Dashboard overview(@RequestParam(required=false) Long classId,@RequestParam(required=false) Long evaluationId){return service.overview(guard.currentUserId(),classId,evaluationId);}
}
