package org.afritechinnovations.controler.self;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.TeacherEmployeeFileService;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/me/teacher/employee-files")
public class TeacherEmployeeFileController {
    private final AccessGuard guard;private final TeacherEmployeeFileService service;
    @GetMapping public List<TeacherEmployeeFileService.Choice> list(){return service.list(guard.currentUserId());}
    @GetMapping("/{teacherId}") public TeacherEmployeeFileService.File read(@PathVariable Long teacherId,@RequestParam(required=false) String month){return service.read(guard.currentUserId(),teacherId,month);}
    @PostMapping("/{teacherId}/requests") @ResponseStatus(HttpStatus.CREATED)
    public FamilyContactDto.ConversationSummary request(@PathVariable Long teacherId,@Valid @RequestBody TeacherEmployeeFileService.ActionRequest request){return service.request(guard.currentUserId(),teacherId,request);}
}
