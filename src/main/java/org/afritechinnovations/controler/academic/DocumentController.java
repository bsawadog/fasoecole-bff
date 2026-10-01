package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.DocumentDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.academic.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final AccessGuard guard;

    @GetMapping("/by-class/{classId}")
    public List<DocumentDto> getByClass(@PathVariable Long classId) {
        guard.requireSchoolMember(guard.schoolOfClass(classId));
        return documentService.findByClass(classId);
    }

    @GetMapping
    public List<DocumentDto> getBySchool(@RequestParam Long schoolId) {
        guard.requireSchoolMember(schoolId);
        return documentService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public DocumentDto getById(@PathVariable Long id) {
        DocumentDto document = documentService.findById(id);
        guard.requireSchoolMember(document.getSchoolId());
        return document;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto create(@RequestBody DocumentDto dto) {
        guard.requireSchoolStaff(dto.getSchoolId());
        if (dto.getClassId() != null) {
            guard.requireSame(dto.getSchoolId(), guard.schoolOfClass(dto.getClassId()), "classe");
        }
        dto.setUploadedBy(guard.currentUserId());
        return documentService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        DocumentDto document = documentService.findById(id);
        if (!guard.currentUserId().equals(document.getUploadedBy())) {
            guard.requireOwnedSchool(document.getSchoolId());
        }
        documentService.delete(id);
    }
}
