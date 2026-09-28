package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.SubjectDto;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.academic.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public List<SubjectDto> findBySchool(Long schoolId) {
        return subjectRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public SubjectDto findById(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Matière introuvable: " + id));
        return toDto(subject);
    }

    public SubjectDto create(SubjectDto dto) {
        Subject subject = Subject.builder()
                .school(School.builder().id(dto.getSchoolId()).build())
                .name(dto.getName())
                .code(dto.getCode())
                .build();
        return toDto(subjectRepository.save(subject));
    }

    public SubjectDto update(Long id, SubjectDto dto) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Matière introuvable: " + id));
        subject.setName(dto.getName());
        subject.setCode(dto.getCode());
        return toDto(subjectRepository.save(subject));
    }

    public void delete(Long id) {
        subjectRepository.deleteById(id);
    }

    private SubjectDto toDto(Subject subject) {
        return SubjectDto.builder()
                .id(subject.getId())
                .schoolId(subject.getSchool().getId())
                .name(subject.getName())
                .code(subject.getCode())
                .build();
    }
}
