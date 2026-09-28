package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findBySchoolClassIdOrderByCreatedAtDesc(Long classId);

    List<Document> findBySchoolId(Long schoolId);
}