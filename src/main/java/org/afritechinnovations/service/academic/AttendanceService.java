package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.AttendanceDto;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    public List<AttendanceDto> findByStudent(Long studentId) {
        return attendanceRepository.findByStudentIdOrderByAttendanceDateDesc(studentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<AttendanceDto> findUnjustifiedAbsences(Long classId, LocalDate start, LocalDate end) {
        return attendanceRepository.findUnjustifiedAbsences(classId, AttendanceStatus.ABSENT, start, end)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AttendanceDto findById(Long id) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Présence introuvable: " + id));
        return toDto(attendance);
    }

    public AttendanceDto create(AttendanceDto dto) {
        Attendance attendance = attendanceRepository.findByStudentIdAndSchoolClassIdAndAttendanceDate(
                dto.getStudentId(),dto.getClassId(),dto.getAttendanceDate()).orElseGet(() -> Attendance.builder()
                .student(Student.builder().id(dto.getStudentId()).build())
                .schoolClass(SchoolClass.builder().id(dto.getClassId()).build())
                .attendanceDate(dto.getAttendanceDate())
                .status(dto.getStatus())
                .justification(dto.getJustification())
                .build());
        attendance.setStatus(dto.getStatus());
        attendance.setJustification(dto.getJustification());
        return toDto(attendanceRepository.save(attendance));
    }

    public AttendanceDto update(Long id, AttendanceDto dto) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Présence introuvable: " + id));
        attendance.setStatus(dto.getStatus());
        attendance.setJustification(dto.getJustification());
        return toDto(attendanceRepository.save(attendance));
    }

    public void delete(Long id) {
        attendanceRepository.deleteById(id);
    }

    private AttendanceDto toDto(Attendance attendance) {
        return AttendanceDto.builder()
                .id(attendance.getId())
                .studentId(attendance.getStudent().getId())
                .classId(attendance.getSchoolClass().getId())
                .attendanceDate(attendance.getAttendanceDate())
                .status(attendance.getStatus())
                .justification(attendance.getJustification())
                .build();
    }
}
