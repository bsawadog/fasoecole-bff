package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    List<Teacher> findBySchoolId(Long schoolId);

    List<Teacher> findByUserId(Long userId);
}