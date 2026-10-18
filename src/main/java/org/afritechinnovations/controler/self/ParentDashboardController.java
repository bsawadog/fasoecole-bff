package org.afritechinnovations.controler.self;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.ParentDashboardService;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/me/parent/dashboard")
public class ParentDashboardController {
 private final AccessGuard guard;private final ParentDashboardService service;
 @GetMapping public ParentDashboardService.Overview overview(@RequestParam Long studentId){return service.overview(guard.currentUserId(),studentId);}
}
