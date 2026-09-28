package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.DocumentDto;
import org.afritechinnovations.model.academic.Document;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.academic.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;

    public List<DocumentDto> findByClass(Long classId) {
        return documentRepository.findBySchoolClassIdOrderByCreatedAtDesc(classId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<DocumentDto> findBySchool(Long schoolId) {
        return documentRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public DocumentDto findById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable: " + id));
        return toDto(document);
    }

    public DocumentDto create(DocumentDto dto) {
        Document document = Document.builder()
                .school(School.builder().id(dto.getSchoolId()).build())
                .schoolClass(dto.getClassId() != null ? SchoolClass.builder().id(dto.getClassId()).build() : null)
                .uploadedBy(User.builder().id(dto.getUploadedBy()).build())
                .title(dto.getTitle())
                .fileUrl(dto.getFileUrl())
                .type(dto.getType())
                .build();
        return toDto(documentRepository.save(document));
    }

    public void delete(Long id) {
        documentRepository.deleteById(id);
    }

    private DocumentDto toDto(Document document) {
        return DocumentDto.builder()
                .id(document.getId())
                .schoolId(document.getSchool().getId())
                .classId(document.getSchoolClass() != null ? document.getSchoolClass().getId() : null)
                .uploadedBy(document.getUploadedBy().getId())
                .title(document.getTitle())
                .fileUrl(document.getFileUrl())
                .type(document.getType())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
