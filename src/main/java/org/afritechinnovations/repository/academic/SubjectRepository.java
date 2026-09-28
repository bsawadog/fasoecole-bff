package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findBySchoolId(Long schoolId);
}