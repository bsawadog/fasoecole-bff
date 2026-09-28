package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClassSubjectTeacherRepository extends JpaRepository<ClassSubjectTeacher, Long> {

    List<ClassSubjectTeacher> findBySchoolClassId(Long classId);

    List<ClassSubjectTeacher> findByTeacherId(Long teacherId);

    @Query("""
        SELECT cst FROM ClassSubjectTeacher cst
        JOIN FETCH cst.subject s
        JOIN FETCH cst.schoolClass c
        WHERE cst.teacher.id = :teacherId
        """)
    List<ClassSubjectTeacher> findAllWithSubjectAndClassByTeacherId(@Param("teacherId") Long teacherId);
}