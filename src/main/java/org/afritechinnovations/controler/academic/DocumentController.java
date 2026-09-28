package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.DocumentDto;
import org.afritechinnovations.service.academic.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/by-class/{classId}")
    public List<DocumentDto> getByClass(@PathVariable Long classId) {
        return documentService.findByClass(classId);
    }

    @GetMapping
    public List<DocumentDto> getBySchool(@RequestParam Long schoolId) {
        return documentService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public DocumentDto getById(@PathVariable Long id) {
        return documentService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto create(@RequestBody DocumentDto dto) {
        return documentService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documentService.delete(id);
    }
}
