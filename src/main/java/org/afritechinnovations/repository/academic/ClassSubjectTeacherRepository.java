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

    @Query("""
        SELECT DISTINCT cst FROM ClassSubjectTeacher cst
        JOIN FETCH cst.teacher t
        JOIN FETCH t.user u
        WHERE cst.schoolClass.id = :classId
        """)
    List<ClassSubjectTeacher> findAllWithTeacherByClassId(@Param("classId") Long classId);

    @Query("""
        SELECT cst FROM ClassSubjectTeacher cst
        JOIN FETCH cst.subject s
        JOIN FETCH cst.teacher t
        JOIN FETCH t.user u
        WHERE cst.schoolClass.id = :classId
        """)
    List<ClassSubjectTeacher> findAllWithTeacherAndSubjectByClassId(@Param("classId") Long classId);

    boolean existsBySchoolClassIdAndTeacherId(Long classId, Long teacherId);

    boolean existsBySchoolClassIdAndTeacherIdAndActiveTrue(Long classId, Long teacherId);

    List<ClassSubjectTeacher> findBySchoolClassIdAndTeacherId(Long classId, Long teacherId);

    java.util.Optional<ClassSubjectTeacher> findBySchoolClassIdAndSubjectIdAndTeacherId(Long classId, Long subjectId,
                                                                                       Long teacherId);

    /** Nombre de classes distinctes où chaque enseignant de l'établissement est affecté et actif : [teacherId, count]. */
    @Query("""
        SELECT cst.teacher.id, COUNT(DISTINCT cst.schoolClass.id) FROM ClassSubjectTeacher cst
        WHERE cst.teacher.school.id = :schoolId AND cst.active = true
        GROUP BY cst.teacher.id
        """)
    List<Object[]> countActiveClassesByTeacher(@Param("schoolId") Long schoolId);

    boolean existsBySchoolClassIdAndSubjectIdAndTeacherId(Long classId, Long subjectId, Long teacherId);
}