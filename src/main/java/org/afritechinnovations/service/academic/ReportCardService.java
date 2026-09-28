package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.ReportCardDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.ReportCard;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.academic.ReportCardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportCardService {

    private final ReportCardRepository reportCardRepository;

    public List<ReportCardDto> findByStudent(Long studentId) {
        return reportCardRepository.findByStudentId(studentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public ReportCardDto findByStudentAcademicYearAndTerm(Long studentId, Long academicYearId, String term) {
        ReportCard reportCard = reportCardRepository
                .findByStudentIdAndAcademicYearIdAndTerm(studentId, academicYearId, term)
                .orElseThrow(() -> new IllegalArgumentException("Bulletin introuvable"));
        return toDto(reportCard);
    }

    public ReportCardDto findById(Long id) {
        ReportCard reportCard = reportCardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bulletin introuvable: " + id));
        return toDto(reportCard);
    }

    public ReportCardDto create(ReportCardDto dto) {
        ReportCard reportCard = ReportCard.builder()
                .student(Student.builder().id(dto.getStudentId()).build())
                .academicYear(AcademicYear.builder().id(dto.getAcademicYearId()).build())
                .term(dto.getTerm())
                .average(dto.getAverage())
                .rank(dto.getRank())
                .comment(dto.getComment())
                .validated(dto.getValidated() != null ? dto.getValidated() : false)
                .build();
        return toDto(reportCardRepository.save(reportCard));
    }

    public ReportCardDto update(Long id, ReportCardDto dto) {
        ReportCard reportCard = reportCardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bulletin introuvable: " + id));
        reportCard.setAverage(dto.getAverage());
        reportCard.setRank(dto.getRank());
        reportCard.setComment(dto.getComment());
        reportCard.setValidated(dto.getValidated());
        return toDto(reportCardRepository.save(reportCard));
    }

    public void delete(Long id) {
        reportCardRepository.deleteById(id);
    }

    private ReportCardDto toDto(ReportCard reportCard) {
        return ReportCardDto.builder()
                .id(reportCard.getId())
                .studentId(reportCard.getStudent().getId())
                .academicYearId(reportCard.getAcademicYear().getId())
                .term(reportCard.getTerm())
                .average(reportCard.getAverage())
                .rank(reportCard.getRank())
                .comment(reportCard.getComment())
                .validated(reportCard.getValidated())
                .build();
    }
}
