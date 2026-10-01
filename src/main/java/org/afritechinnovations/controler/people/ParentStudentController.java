package org.afritechinnovations.controler.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ParentStudentDto;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.people.ParentService;
import org.afritechinnovations.service.people.ParentStudentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parent-students")
@RequiredArgsConstructor
public class ParentStudentController {

    private final ParentStudentService parentStudentService;
    private final ParentService parentService;
    private final ParentStudentRepository parentStudentRepository;
    private final AccessGuard guard;

    /** Le parent voit tous ses enfants ; un propriétaire ne voit que les enfants de ses établissements. */
    @GetMapping("/by-parent/{parentId}")
    public List<ParentStudentDto> getByParent(@PathVariable Long parentId) {
        Long parentUserId = parentService.findById(parentId).getUserId();
        List<ParentStudentDto> links = parentStudentService.findByParent(parentId);
        if (guard.isSuperAdmin() || guard.currentUserId().equals(parentUserId)) {
            return links;
        }
        guard.requireUserManager(parentUserId);
        return links.stream()
                .filter(link -> guard.ownsSchool(guard.schoolOfStudent(link.getStudentId())))
                .toList();
    }

    @GetMapping("/by-student/{studentId}")
    public List<ParentStudentDto> getByStudent(@PathVariable Long studentId) {
        guard.requireStudentReader(studentId);
        return parentStudentService.findByStudent(studentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParentStudentDto create(@RequestBody ParentStudentDto dto) {
        guard.requireSchoolModule(guard.schoolOfStudent(dto.getStudentId()), StaffModule.STUDENTS);
        parentService.findById(dto.getParentId());
        return parentStudentService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ParentStudent link = parentStudentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lien parent-élève introuvable : " + id));
        guard.requireSchoolModule(guard.schoolOfStudent(link.getStudent().getId()), StaffModule.STUDENTS);
        parentStudentService.delete(id);
    }
}
