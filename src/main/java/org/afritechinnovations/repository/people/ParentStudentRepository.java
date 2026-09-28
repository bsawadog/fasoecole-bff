package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.ParentStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, Long> {

    List<ParentStudent> findByParentId(Long parentId);

    List<ParentStudent> findByStudentId(Long studentId);

    @Query("""
        SELECT ps FROM ParentStudent ps
        JOIN FETCH ps.student s
        JOIN FETCH s.user u
        WHERE ps.parent.id = :parentId
        """)
    List<ParentStudent> findChildrenWithUserByParentId(@Param("parentId") Long parentId);
}