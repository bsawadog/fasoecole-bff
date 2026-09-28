package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ParentStudentDto;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ParentStudentService {

    private final ParentStudentRepository parentStudentRepository;

    public List<ParentStudentDto> findByParent(Long parentId) {
        return parentStudentRepository.findByParentId(parentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<ParentStudentDto> findByStudent(Long studentId) {
        return parentStudentRepository.findByStudentId(studentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public ParentStudentDto create(ParentStudentDto dto) {
        ParentStudent link = ParentStudent.builder()
                .parent(Parent.builder().id(dto.getParentId()).build())
                .student(Student.builder().id(dto.getStudentId()).build())
                .relationship(dto.getRelationship())
                .build();
        return toDto(parentStudentRepository.save(link));
    }

    public void delete(Long id) {
        parentStudentRepository.deleteById(id);
    }

    private ParentStudentDto toDto(ParentStudent link) {
        return ParentStudentDto.builder()
                .id(link.getId())
                .parentId(link.getParent().getId())
                .studentId(link.getStudent().getId())
                .relationship(link.getRelationship())
                .build();
    }
}
