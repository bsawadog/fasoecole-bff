package org.afritechinnovations.repository.academic;


import org.afritechinnovations.model.academic.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LevelRepository extends JpaRepository<Level, Long> {

    List<Level> findBySchoolIdOrderByOrderIndexAsc(Long schoolId);
}
